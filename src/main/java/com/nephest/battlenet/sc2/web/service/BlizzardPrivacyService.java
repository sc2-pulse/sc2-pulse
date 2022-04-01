// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.*;
import com.nephest.battlenet.sc2.model.local.*;
import com.nephest.battlenet.sc2.model.local.dao.*;
import com.nephest.battlenet.sc2.util.MiscUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.validation.Validator;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class BlizzardPrivacyService
{

    private static final Logger LOG = LoggerFactory.getLogger(BlizzardPrivacyService.class);

    public static final Duration DATA_TTL = Duration.ofDays(30);
    public static final int CURRENT_SEASON_UPDATES_PER_PERIOD = 3;
    //postgresql param limit / max players in a single ladder / param count
    public static final int LADDER_BATCH_SIZE = 32767 / 400 / 6;
    public static final int ALTERNATIVE_LADDER_BATCH_SIZE = 32767 / 400 / 4;
    public static final Instant DEFAULT_ANONYMIZE_START =
        OffsetDateTime.of(2015, 1, 1, 0, 0, 0, 0, OffsetDateTime.now().getOffset()).toInstant();
    private static final QueueType[] QUEUE_TYPES = QueueType.getTypes(StatsService.VERSION).toArray(QueueType[]::new);
    private static final BaseLeague.LeagueType[] LEAGUE_TYPES = BaseLeague.LeagueType.values();

    private final BlizzardSC2API api;
    private final StatsService statsService;
    private final AlternativeLadderService alternativeLadderService;
    private final SeasonDAO seasonDAO;
    private final AccountDAO accountDAO;
    private final PlayerCharacterDAO playerCharacterDAO;
    private final ExecutorService dbExecutorService;
    private final ExecutorService webExecutorService;
    private final SC2WebServiceUtil sc2WebServiceUtil;
    private final Predicate<BlizzardTeamMember> teamMemberPredicate;
    private final Predicate<BlizzardProfileTeamMember> profileTeamMemberPredicate;
    private LongVar lastUpdatedSeason;
    private InstantVar lastUpdatedSeasonInstant;
    private InstantVar lastUpdatedCurrentSeasonInstant;
    private InstantVar lastAnonymizeInstant;

    @Autowired
    public BlizzardPrivacyService
    (
        BlizzardSC2API api,
        StatsService statsService,
        AlternativeLadderService alternativeLadderService,
        SeasonDAO seasonDAO,
        VarDAO varDAO,
        AccountDAO accountDAO,
        PlayerCharacterDAO playerCharacterDAO,
        @Qualifier("dbExecutorService") ExecutorService dbExecutorService,
        @Qualifier("webExecutorService") ExecutorService webExecutorService,
        Validator validator,
        SC2WebServiceUtil sc2WebServiceUtil
    )
    {
        this.api = api;
        this.statsService = statsService;
        this.alternativeLadderService = alternativeLadderService;
        this.seasonDAO = seasonDAO;
        this.accountDAO = accountDAO;
        this.playerCharacterDAO = playerCharacterDAO;
        this.dbExecutorService = dbExecutorService;
        this.webExecutorService = webExecutorService;
        this.teamMemberPredicate = DAOUtils.beanValidationPredicate(validator);
        this.profileTeamMemberPredicate = DAOUtils.beanValidationPredicate(validator);
        this.sc2WebServiceUtil = sc2WebServiceUtil;
        initVars(varDAO);
    }

    private void initVars(VarDAO varDAO)
    {
        lastUpdatedSeason = new LongVar(varDAO, "blizzard.privacy.season.last", false);
        lastUpdatedSeasonInstant = new InstantVar(varDAO, "blizzard.privacy.season.last.updated", false);
        lastUpdatedCurrentSeasonInstant = new InstantVar(varDAO, "blizzard.privacy.season.current.updated", false);
        lastAnonymizeInstant = new InstantVar(varDAO, "blizzard.privacy.anonymized", false);
        try
        {
            lastUpdatedSeason.load();
            if(lastUpdatedSeason.getValue() == null)
                lastUpdatedSeason.setValueAndSave((long) BlizzardSC2API.FIRST_SEASON - 1);
            lastUpdatedSeasonInstant.load();
            if(lastUpdatedSeasonInstant.getValue() == null)
                lastUpdatedSeasonInstant.setValueAndSave(Instant.now().minusSeconds(DATA_TTL.toSeconds()));
            lastUpdatedCurrentSeasonInstant.load();
            if(lastUpdatedCurrentSeasonInstant.getValue() == null)
                lastUpdatedCurrentSeasonInstant.setValueAndSave(Instant.now().minusSeconds(DATA_TTL.toSeconds()));
            lastAnonymizeInstant.load();
            if(lastAnonymizeInstant.getValue() == null)
                lastAnonymizeInstant.setValueAndSave(DEFAULT_ANONYMIZE_START);
        }
        catch (Exception ex)
        {
            LOG.error(ex.getMessage(), ex);
        }
    }

    protected InstantVar getLastUpdatedSeasonInstantVar()
    {
        return lastUpdatedSeasonInstant;
    }

    protected InstantVar getLastUpdatedCurrentSeasonInstantVar()
    {
        return lastUpdatedCurrentSeasonInstant;
    }

    public void update()
    {
        handleExpiredData();
        Integer season = getSeasonToUpdate();
        if(season == null) return;

        LOG.debug("Updating old names and BattleTags for season {}", season);
        boolean current = seasonDAO.getMaxBattlenetId().equals(season);

        List<Future<?>> dbTasks = new ArrayList<>();
        for(Region region : Region.values())
        {
            if(!statsService.isAlternativeUpdate(region, current))
            {
                dbTasks.add(webExecutorService.submit(()->update(region, season, current)));
            }
            else
            {
                dbTasks.add(webExecutorService.submit(()->alternativeUpdate(region, season)));
            }
        }
        MiscUtil.awaitAndLogExceptions(dbTasks, true);
        lastUpdatedSeasonInstant.setValueAndSave(Instant.now());
        if(current)
        {
            lastUpdatedCurrentSeasonInstant.setValueAndSave(Instant.now());
        }
        else
        {
            lastUpdatedSeason.setValueAndSave((long) season);
        }

        LOG.info("Updated old names and BattleTags for season {}", season);
    }

    private void handleExpiredData()
    {
        int removedAccounts = accountDAO.removeEmptyAccounts();
        if(removedAccounts > 0) LOG.info("Removed {} empty accounts", removedAccounts);
        anonymizeExpiredData();
    }

    private void anonymizeExpiredData()
    {
        Instant anonymizeInstant = OffsetDateTime.now().minusSeconds(BlizzardPrivacyService.DATA_TTL.toSeconds()).toInstant();
        OffsetDateTime from = OffsetDateTime.ofInstant(lastAnonymizeInstant.getValue(), ZoneId.systemDefault());
        int accounts = accountDAO.anonymizeExpiredAccounts(from);
        int characters = playerCharacterDAO.anonymizeExpiredCharacters(from);
        lastAnonymizeInstant.setValueAndSave(anonymizeInstant);
        if(accounts > 0 || characters > 0) LOG.info("Anonymized {} accounts and {} characters", accounts, characters);
    }

    private void update(Region region, int seasonId, boolean currentSeason)
    {
        BlizzardSeason bSeason = sc2WebServiceUtil.getExternalOrExistingSeason(region, seasonId);
        List<Future<?>> dbTasks = new ArrayList<>();
        List<Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>> ladderIds =
            statsService.getLadderIds(StatsService.getLeagueIds(bSeason, region, QUEUE_TYPES, LEAGUE_TYPES), currentSeason);
        api.getLadders(ladderIds, 1, new EnumMap<>(Region.class))
            .buffer(LADDER_BATCH_SIZE)
            .map(this::extractPrivateInfo)
            .toStream()
            .forEach(l->dbTasks.add(dbExecutorService.submit(()->
                LOG.debug("Updated {} accounts and characters", playerCharacterDAO.updateAccountsAndCharacters(l)))));
        MiscUtil.awaitAndLogExceptions(dbTasks, true);
    }

    private List<Tuple2<Account, PlayerCharacter>> extractPrivateInfo
    (List<Tuple2<BlizzardLadder, Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>>> ladders)
    {
        if(ladders.isEmpty()) return List.of();
        Region region = ladders.get(0).getT2().getT2();
        return ladders.stream()
            .flatMap(l->Arrays.stream(l.getT1().getTeams()))
            .flatMap(t->Arrays.stream(t.getMembers()))
            .filter(teamMemberPredicate)
            .map(m->
            {
                Account account = Account.of(m.getAccount(), region);
                PlayerCharacter character = PlayerCharacter.of(account, region, m.getCharacter());
                return Tuples.of(account, character);
            })
            .collect(Collectors.toList());
    }

    private void alternativeUpdate(Region region, int seasonId)
    {
        Season season = new Season(null, seasonId, region, null, null, null, null);
        List<Tuple3<Region, BlizzardPlayerCharacter[], Long>> ladderIds = alternativeLadderService
            .getExistingLadderIds(season, QUEUE_TYPES, LEAGUE_TYPES);
        List<Future<?>> dbTasks = new ArrayList<>();
        api.getProfileLadders(ladderIds, Set.of(QUEUE_TYPES), alternativeLadderService.isProfileLadderWebRegion(region))
            .buffer(ALTERNATIVE_LADDER_BATCH_SIZE)
            .map(this::extractAlternativePrivateInfo)
            .toStream()
            .forEach(l->dbTasks.add(dbExecutorService.submit(()->
                LOG.debug("Updated {} characters", playerCharacterDAO.updateCharacters(l)))));
        MiscUtil.awaitAndLogExceptions(dbTasks, true);
    }

    private PlayerCharacter[] extractAlternativePrivateInfo
    (List<Tuple2<BlizzardProfileLadder, Tuple3<Region, BlizzardPlayerCharacter[], Long>>> ladders)
    {
        if(ladders.isEmpty()) return new PlayerCharacter[0];

        Region region = ladders.get(0).getT2().getT1();
        return ladders.stream()
            .flatMap(l->Arrays.stream(l.getT1().getLadderTeams()))
            .flatMap(t->Arrays.stream(t.getTeamMembers()))
            .filter(profileTeamMemberPredicate)
            .map(m->PlayerCharacter.of(new Account(), region, m))
            .toArray(PlayerCharacter[]::new);
    }

    protected Integer getSeasonToUpdate()
    {
        Integer lastSeason = seasonDAO.getMaxBattlenetId();
        if(lastSeason == null || lastSeason < BlizzardSC2API.FIRST_SEASON) return null;

        int updatesPerPeriod = ((lastSeason - BlizzardSC2API.FIRST_SEASON) + 1) + CURRENT_SEASON_UPDATES_PER_PERIOD;
        long secondsBetweenUpdates = DATA_TTL.toSeconds() / updatesPerPeriod;
        if(Instant.now().getEpochSecond() - lastUpdatedSeasonInstant.getValue().getEpochSecond() < secondsBetweenUpdates)
            return null;

        long secondsBetweenCurrentSeasonUpdates = DATA_TTL.toSeconds() / CURRENT_SEASON_UPDATES_PER_PERIOD;
        return Instant.now().getEpochSecond() - lastUpdatedCurrentSeasonInstant.getValue().getEpochSecond() >= secondsBetweenCurrentSeasonUpdates
            ? lastSeason
            : (int) (lastUpdatedSeason.getValue() + 1 >= lastSeason ? BlizzardSC2API.FIRST_SEASON : lastUpdatedSeason.getValue() + 1);
    }

}
