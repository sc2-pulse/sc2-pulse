// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLadder;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLeague;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLeagueTier;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLegacyProfile;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardPlayerCharacter;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardProfileLadder;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardProfileTeamMember;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardSeason;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTeamMember;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTierDivision;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.InstantVar;
import com.nephest.battlenet.sc2.model.local.LongVar;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.TimerVar;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.DAOUtils;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.local.inner.AccountCharacterData;
import com.nephest.battlenet.sc2.model.local.inner.ClanMemberEventData;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.util.MiscUtil;
import com.nephest.battlenet.sc2.util.SingleRunnable;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.validation.Validator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

@Service
public class BlizzardPrivacyService
{

    private static final Logger LOG = LoggerFactory.getLogger(BlizzardPrivacyService.class);

    public static final Duration DATA_TTL = Duration.ofDays(30);
    public static final Duration ANONYMIZATION_DATA_TIME_FRAME = Duration.ofMinutes(60);
    public static final Duration FULL_ANONYMIZATION_DATA_TIME_FRAME = DATA_TTL.dividedBy(3);
    public static final Duration CHARACTER_UPDATE_TIME_FRAME = Duration.ofMinutes(4);
    public static final Duration CHARACTER_UPDATE_EXPIRATION_THRESHOLD = Duration.ofDays(15);
    public static final Duration CHARACTER_UPDATED_MAX = DATA_TTL.minus(CHARACTER_UPDATE_EXPIRATION_THRESHOLD);
    public static final Duration OLD_LADDER_DATA_TTL = Duration.ofDays(20);
    public static final int CURRENT_SEASON_UPDATES_PER_PERIOD = 3;
    public static final int OLD_SEASON_COUNT = (int) OLD_LADDER_DATA_TTL.toDays()
        - CURRENT_SEASON_UPDATES_PER_PERIOD;
    public static final int ACCOUNT_AND_CHARACTER_BATCH_SIZE = 250;
    public static final int CHARACTER_UPDATES_PER_TTL = (int) (CHARACTER_UPDATE_EXPIRATION_THRESHOLD.toSeconds() / CHARACTER_UPDATE_TIME_FRAME.toSeconds());
    public static final OffsetDateTime DEFAULT_ANONYMIZE_START =
        OffsetDateTime.of(2015, 1, 1, 0, 0, 0, 0, SC2Pulse.offsetDateTime().getOffset());
    private static final Map<QueueType, Set<BaseLeague.LeagueType>> UPDATE_DATA
        = LadderUpdateContext.ALL;
    public static final String REQUEST_LIMIT_PRIORITY_NAME = "privacy";
    public static final int REQUEST_LIMIT_PRIORITY_SLOTS = 1;

    private final BlizzardSC2API api;
    private final StatsService statsService;
    private final AlternativeLadderService alternativeLadderService;
    private final SeasonDAO seasonDAO;
    private final AccountDAO accountDAO;
    private final PlayerCharacterDAO playerCharacterDAO;
    private final ClanService clanService;
    private final ExecutorService dbExecutorService;
    private final Scheduler secondaryDbScheduler;
    private final ExecutorService webExecutorService;
    private final SC2WebServiceUtil sc2WebServiceUtil;
    private final Predicate<BlizzardTeamMember> teamMemberPredicate;
    private final Predicate<BlizzardProfileTeamMember> profileTeamMemberPredicate;
    private final Predicate<BlizzardLegacyProfile> legacyProfilePredicate;
    private final GlobalContext globalContext;
    private LongVar lastUpdatedSeason;
    private LongVar lastUpdatedCharacterId;
    private InstantVar lastUpdatedSeasonInstant;
    private InstantVar lastUpdatedCurrentSeasonInstant;
    private InstantVar lastAnonymizeInstant;
    private TimerVar fullAnonymizeTask;
    private InstantVar lastUpdatedCharacterInstant;
    private Future<?> characterUpdateTask = CompletableFuture.completedFuture(null);
    private final SingleRunnable updateOldDataTask;
    private final boolean updateCharacterProfiles;

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
        ClanService clanService,
        @Qualifier("dbExecutorService") ExecutorService dbExecutorService,
        @Qualifier("secondaryDbScheduler") Scheduler secondaryDbScheduler,
        @Qualifier("webExecutorService") ExecutorService webExecutorService,
        Validator validator,
        SC2WebServiceUtil sc2WebServiceUtil,
        GlobalContext globalContext,
        @Value("${com.nephest.battlenet.sc2.privacy.character.profile.update:#{'true'}}") boolean updateCharacterProfiles
    )
    {
        this.api = api;
        this.statsService = statsService;
        this.alternativeLadderService = alternativeLadderService;
        this.seasonDAO = seasonDAO;
        this.accountDAO = accountDAO;
        this.playerCharacterDAO = playerCharacterDAO;
        this.clanService = clanService;
        this.dbExecutorService = dbExecutorService;
        this.secondaryDbScheduler = secondaryDbScheduler;
        this.webExecutorService = webExecutorService;
        this.teamMemberPredicate = DAOUtils.beanValidationPredicate(validator);
        this.profileTeamMemberPredicate = DAOUtils.beanValidationPredicate(validator);
        this.legacyProfilePredicate = DAOUtils.beanValidationPredicate(validator);
        this.sc2WebServiceUtil = sc2WebServiceUtil;
        this.globalContext = globalContext;
        initVars(varDAO);
        api.addRequestLimitPriority(REQUEST_LIMIT_PRIORITY_NAME, REQUEST_LIMIT_PRIORITY_SLOTS);
        updateOldDataTask = new SingleRunnable(this::doUpdateOldSeasons, webExecutorService);
        this.updateCharacterProfiles = updateCharacterProfiles;
        if(!this.updateCharacterProfiles) LOG.warn("Character profile updates are disabled");
    }

    private void initVars(VarDAO varDAO)
    {
        lastUpdatedSeason = new LongVar(varDAO, "blizzard.privacy.season.last", false);
        lastUpdatedCharacterId = new LongVar(varDAO, "blizzard.privacy.character.id", false);
        lastUpdatedSeasonInstant = new InstantVar(varDAO, "blizzard.privacy.season.last.updated", false);
        lastUpdatedCurrentSeasonInstant = new InstantVar(varDAO, "blizzard.privacy.season.current.updated", false);
        lastAnonymizeInstant = new InstantVar(varDAO, "blizzard.privacy.anonymized", false);
        fullAnonymizeTask = new TimerVar
        (
            varDAO,
            "blizzard.privacy.anonymized.full",
            false,
            FULL_ANONYMIZATION_DATA_TIME_FRAME,
            ()->
            {
                int count = accountDAO.anonymizeExpiredAccounts(DEFAULT_ANONYMIZE_START);
                LOG.info("Executed full account anonymization. Anonymized accounts: {}.", count);
            }
        );
        lastUpdatedCharacterInstant = new InstantVar(varDAO, "blizzard.privacy.character.updated", false);
        try
        {
            lastUpdatedSeason.load();
            if(lastUpdatedSeason.getValue() == null)
                lastUpdatedSeason.setValueAndSave((long) BlizzardSC2API.FIRST_SEASON - 1);
            lastUpdatedCharacterId.load();
            if(lastUpdatedCharacterId.getValue() == null)
                lastUpdatedCharacterId.setValueAndSave(Long.MAX_VALUE);
            lastUpdatedSeasonInstant.load();
            if(lastUpdatedSeasonInstant.getValue() == null)
                lastUpdatedSeasonInstant.setValueAndSave(SC2Pulse.instant().minusSeconds(DATA_TTL.toSeconds()));
            lastUpdatedCurrentSeasonInstant.load();
            if(lastUpdatedCurrentSeasonInstant.getValue() == null)
                lastUpdatedCurrentSeasonInstant.setValueAndSave(SC2Pulse.instant().minusSeconds(DATA_TTL.toSeconds()));
            lastAnonymizeInstant.load();
            if(lastAnonymizeInstant.getValue() == null)
                lastAnonymizeInstant.setValueAndSave(DEFAULT_ANONYMIZE_START.toInstant());
            fullAnonymizeTask.load();
            lastUpdatedCharacterInstant.load();
            if(lastUpdatedCharacterInstant.getValue() == null)
                lastUpdatedCharacterInstant.setValueAndSave(DEFAULT_ANONYMIZE_START.toInstant());
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

    protected InstantVar getLastUpdatedCharacterInstant()
    {
        return lastUpdatedCharacterInstant;
    }

    protected LongVar getLastUpdatedCharacterId()
    {
        return lastUpdatedCharacterId;
    }

    public Future<?> getCharacterUpdateTask()
    {
        return characterUpdateTask;
    }

    public CompletableFuture<Void> getUpdateOldDataTask()
    {
        return updateOldDataTask.getFuture();
    }

    public Future<?> update()
    {
        handleExpiredData();
        if(shouldUpdateCharacters() && (characterUpdateTask == null || characterUpdateTask.isDone()))
            characterUpdateTask = webExecutorService.submit(this::updateCharacters);
        return characterUpdateTask;
    }

    @Scheduled(cron="0 0 6 * * *", zone = "UTC")
    public void updateOldSeasons()
    {
        updateOldDataTask.tryRun();
    }

    private void doUpdateOldSeasons()
    {
        Integer season = getSeasonToUpdate();
        if(season == null) return;

        LOG.debug("Updating old names and BattleTags for season {}", season);
        boolean current = seasonDAO.getMaxBattlenetId().equals(season);

        List<Future<Void>> dbTasks = new ArrayList<>();
        for(Region region : globalContext.getActiveRegions())
        {
            if(!statsService.isAlternativeUpdate(region, current))
            {
                dbTasks.add(webExecutorService.submit(()->update(region, season, current), null));
            }
            else
            {
                dbTasks.add(webExecutorService.submit(()->alternativeUpdate(region, season), null));
            }
        }
        MiscUtil.awaitAndLogExceptions(dbTasks, true);
        lastUpdatedSeasonInstant.setValueAndSave(SC2Pulse.instant());
        if(current)
        {
            lastUpdatedCurrentSeasonInstant.setValueAndSave(SC2Pulse.instant());
        }
        else
        {
            lastUpdatedSeason.setValueAndSave((long) season);
        }

        LOG.info("Updated old names and BattleTags for season {}", season);
    }

    private boolean shouldHandleExpiredData()
    {
        return lastAnonymizeInstant.getValue().plusSeconds(BlizzardPrivacyService.DATA_TTL.toSeconds())
            .isBefore(SC2Pulse.instant().minusSeconds(ANONYMIZATION_DATA_TIME_FRAME.toSeconds()));
    }

    private void handleExpiredData()
    {
        if(!shouldHandleExpiredData()) return;

        int removedAccounts = accountDAO.removeEmptyAccounts();
        if(removedAccounts > 0) LOG.info("Removed {} empty accounts", removedAccounts);
        anonymizeExpiredData();
    }

    private void anonymizeExpiredData()
    {
        fullAnonymizeTask.runIfAvailable().block();
        Instant anonymizeInstant = SC2Pulse.offsetDateTime().minusSeconds(BlizzardPrivacyService.DATA_TTL.toSeconds()).toInstant();
        OffsetDateTime from = OffsetDateTime.ofInstant(lastAnonymizeInstant.getValue(), ZoneId.systemDefault());
        int accounts = accountDAO.anonymizeExpiredAccounts(from);
        int characters = playerCharacterDAO.anonymizeExpiredCharacters(from);
        lastAnonymizeInstant.setValueAndSave(anonymizeInstant);
        if(accounts > 0 || characters > 0) LOG.info("Anonymized {} accounts and {} characters", accounts, characters);
    }

    protected void update(Region region, int seasonId, boolean currentSeason)
    {
        BlizzardSeason bSeason = sc2WebServiceUtil.getExternalOrExistingSeason(region, seasonId);
        List<Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>> ladderIds =
            statsService.getLadderIds(StatsService.getLeagueIds(bSeason, region, UPDATE_DATA), currentSeason);
        api.getLadders(ladderIds, -1, Map.of(), REQUEST_LIMIT_PRIORITY_NAME)
            .flatMap(l->Flux.fromStream(extractPrivateInfo(l, seasonId, currentSeason)))
            .buffer(ACCOUNT_AND_CHARACTER_BATCH_SIZE)
            .flatMap(l->process(l, currentSeason))
            .blockLast();
    }

    private Stream<Tuple2<ClanMemberEventData, AccountCharacterData>> extractPrivateInfo
    (
        Tuple2<BlizzardLadder, Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>> ladder,
        int seasonId,
        boolean currentSeason
    )
    {
        Region region = ladder.getT2().getT2();
        boolean fresh = !statsService.isAlternativeUpdate(region, true) && currentSeason;
        return Stream.of(ladder)
            .flatMap(l->Arrays.stream(l.getT1().getTeams()))
            .flatMap(t->Arrays.stream(t.getMembers()))
            .filter(teamMemberPredicate)
            .map(m->
            {
                Account account = Account.of(m.getAccount(), region);
                PlayerCharacter character = PlayerCharacter.of(account, region, m.getCharacter());
                return Tuples.of
                (
                    StatsService.extractCharacterClanData(ladder.getT1(), m, character),
                    new AccountCharacterData(account, character, fresh, seasonId)
                );
            });
    }

    private Mono<Void> process
    (
        List<Tuple2<ClanMemberEventData, AccountCharacterData>> members,
        boolean currentSeason
    )
    {
        return WebServiceUtil.getOnErrorLogAndSkipMono(Flux.fromIterable(members)
            .map(Tuple2::getT2)
            .collect(Collectors.toSet())
            .flatMap(privateData->Mono.fromCallable(()->
                playerCharacterDAO.updateAccountsAndCharacters(privateData))
                .subscribeOn(secondaryDbScheduler))
            .doOnNext(data->LOG.debug("Updated {} accounts and characters", data.size()))
            .then
            (
                currentSeason
                    ? Flux.fromIterable(members)
                        .map(Tuple2::getT1)
                        .filter(data->data.getCharacter().getId() != null)
                        .collectList()
                        .flatMap(clans->Mono.fromRunnable(()->clanService.saveClans(clans))
                            .subscribeOn(secondaryDbScheduler))
                    : Mono.empty()
            )
            .then());
    }

    private void alternativeUpdate(Region region, int seasonId)
    {
        Season season = new Season(null, seasonId, region, null, null, null, null);
        List<Tuple3<Region, BlizzardPlayerCharacter[], Long>> ladderIds = alternativeLadderService
            .getExistingLadderIds(season, UPDATE_DATA);
        api.getProfileLadders
        (
            ladderIds,
            UPDATE_DATA.keySet(),
            alternativeLadderService.isProfileLadderWebRegion(region),
            REQUEST_LIMIT_PRIORITY_NAME
        )
            .flatMap(l->Flux.fromStream(extractAlternativePrivateInfo(l)))
            .buffer(ACCOUNT_AND_CHARACTER_BATCH_SIZE)
            .flatMap(this::processAlternative)
            .blockLast();
    }

    private Stream<Tuple2<ClanMemberEventData, PlayerCharacter>> extractAlternativePrivateInfo
    (Tuple2<BlizzardProfileLadder, Tuple3<Region, BlizzardPlayerCharacter[], Long>> ladder)
    {
        Region region = ladder.getT2().getT1();
        return Stream.of(ladder)
            .flatMap(l->Arrays.stream(l.getT1().getLadderTeams()))
            .flatMap(t->Arrays.stream(t.getTeamMembers()))
            .filter(profileTeamMemberPredicate)
            .map(m->{
                PlayerCharacter character = PlayerCharacter.of(new Account(), region, m);
                return Tuples.of
                (
                    AlternativeLadderService.extractClan(character, m, ladder.getT1()),
                    character
                );
            });
    }

    private Mono<Void> processAlternative
    (
        List<Tuple2<ClanMemberEventData, PlayerCharacter>> members
    )
    {
        return WebServiceUtil.getOnErrorLogAndSkipMono(Flux.fromIterable(members)
            .map(Tuple2::getT2)
            .collect(Collectors.toSet())
            .flatMap(chars->Mono.fromCallable(()->playerCharacterDAO.updateCharacters(chars))
                .subscribeOn(secondaryDbScheduler))
            .doOnNext(chars->LOG.debug("Updated {} characters", chars.size()))
            .thenMany(Flux.fromIterable(members))
            .map(Tuple2::getT1)
            .filter(data->data.getCharacter().getId() != null)
            .collectList()
            .flatMap(clans-> Mono.fromRunnable(()->clanService.saveClans(clans))
                .subscribeOn(secondaryDbScheduler))
            .then());
    }

    protected Integer getSeasonToUpdate()
    {
        Integer lastSeason = seasonDAO.getMaxBattlenetId();
        if(lastSeason == null || lastSeason < BlizzardSC2API.FIRST_SEASON) return null;

        long secondsBetweenCurrentSeasonUpdates = OLD_LADDER_DATA_TTL.toSeconds() / CURRENT_SEASON_UPDATES_PER_PERIOD;
        return SC2Pulse.instant().getEpochSecond() - lastUpdatedCurrentSeasonInstant.getValue().getEpochSecond() >= secondsBetweenCurrentSeasonUpdates
            ? lastSeason
            : (int) (lastUpdatedSeason.getValue() + 1 >= lastSeason
                ? Math.max(lastSeason - OLD_SEASON_COUNT, BlizzardSC2API.FIRST_SEASON)
                : lastUpdatedSeason.getValue() + 1);
    }

    public boolean getUpdateCharactersFlag()
    {
        return updateCharacterProfiles;
    }

    private boolean shouldUpdateCharacters()
    {
        return getUpdateCharactersFlag()
            && lastUpdatedCharacterInstant.getValue().isBefore(SC2Pulse.instant().minus(CHARACTER_UPDATE_TIME_FRAME));
    }

    private int getCharacterBatchSize()
    {
        int characterCount = playerCharacterDAO
            .countByUpdatedMax(SC2Pulse.offsetDateTime().minus(CHARACTER_UPDATED_MAX), globalContext.getActiveRegions());
        return (int) Math.ceil((double) characterCount / CHARACTER_UPDATES_PER_TTL);
    }

    private void updateCharacters()
    {
        int batchSize = getCharacterBatchSize();
        if(batchSize == 0)
        {
            resetCharacterUpdateVars();
            return;
        }

        List<PlayerCharacter> batch = playerCharacterDAO.find
        (
            SC2Pulse.offsetDateTime().minus(CHARACTER_UPDATED_MAX),
            lastUpdatedCharacterId.getValue(),
            globalContext.getActiveRegions(),
            batchSize
        );
        if(batch.isEmpty())
        {
            resetCharacterUpdateVars();
            return;
        }

        List<Future<?>> dbTasks = new ArrayList<>();
        Flux.fromIterable
        (
            batch.stream()
                .collect(Collectors.groupingBy(PlayerCharacter::getRegion))
                .entrySet()
        )
            .flatMap(entry->api.getLegacyProfiles
            (
                entry.getValue(),
                alternativeLadderService.isProfileLadderWebRegion(entry.getKey())
                    || alternativeLadderService.isDiscoveryWebRegion(entry.getKey())
            ))
            .filter(c->legacyProfilePredicate.test(c.getT1()))
            .map(this::extractCharacter)
            .buffer(ACCOUNT_AND_CHARACTER_BATCH_SIZE)
            .toStream()
            .forEach(l->{
                dbTasks.add(dbExecutorService.submit(()->
                    LOG.info("Updated {} characters that are about to expire",
                        playerCharacterDAO.updateCharacters(Set.copyOf(l)).size())));
            });
        lastUpdatedCharacterInstant.setValueAndSave(SC2Pulse.instant());
        lastUpdatedCharacterId.setValueAndSave(batch.get(batch.size() - 1).getId());
    }

    private void resetCharacterUpdateVars()
    {
        lastUpdatedCharacterInstant.setValueAndSave(SC2Pulse.instant());
        lastUpdatedCharacterId.setValueAndSave(Long.MAX_VALUE);
    }


    private PlayerCharacter extractCharacter(Tuple2<BlizzardLegacyProfile, PlayerCharacterNaturalId> bChar)
    {
        return PlayerCharacter.of(new Account(), bChar.getT2().getRegion(), bChar.getT1());
    }

}
