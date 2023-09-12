// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.BasePlayerCharacter;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamFormat;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardPlayerCharacter;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardProfileLadder;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardProfileTeam;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardProfileTeamMember;
import com.nephest.battlenet.sc2.model.blizzard.dao.BlizzardDAO;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.CollectionVar;
import com.nephest.battlenet.sc2.model.local.Division;
import com.nephest.battlenet.sc2.model.local.InstantVar;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.LeagueTier;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.TeamMember;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.DAOUtils;
import com.nephest.battlenet.sc2.model.local.dao.DivisionDAO;
import com.nephest.battlenet.sc2.model.local.dao.FastTeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.LeagueDAO;
import com.nephest.battlenet.sc2.model.local.dao.LeagueTierDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.local.inner.AlternativeTeamData;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.convert.ConversionService;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Validator;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Service
public class AlternativeLadderService
{

    private static final Logger LOG = LoggerFactory.getLogger(AlternativeLadderService.class);
    private static final Map<Region, Integer> SMART_DISCOVERY_MAX = Collections.unmodifiableMap(Map.of(
        Region.US, 200,
        Region.EU, 200,
        Region.KR, 50,
        Region.CN, 100
    ));

    public static final int LADDER_BATCH_SIZE = StatsService.LADDER_BATCH_SIZE;
    public static final Duration DISCOVERY_TIME_FRAME = Duration.ofHours(12);
    public static final Duration ADDITIONAL_WEB_SCAN_TIME_FRAME = Duration.ZERO;
    public static final Duration NON_WEB_SCAN_TIME_FRAME = Duration.ofMinutes(50);
    public static final double WEB_API_ERROR_RATE_THRESHOLD = 50;
    public static final double WEB_API_FORCE_REGION_ERROR_RATE_THRESHOLD = 25;
    public static final int ADDITIONAL_WEB_UPDATES_PER_CYCLE = 2;
    private static final QueueType[] ADDITIONAL_WEB_UPDATE_QUEUE_TYPES = new QueueType[]{QueueType.LOTV_1V1};
    private static final BaseLeague.LeagueType[] ADDITIONAL_WEB_UPDATE_LEAGUE_TYPES
        = BaseLeague.LeagueType.values();
    private static final QueueType[] BIG_ADDITIONAL_WEB_UPDATE_QUEUE_TYPES =
        QueueType.getTypes(StatsService.VERSION).toArray(QueueType[]::new);
    private static final BaseLeague.LeagueType[] BIG_ADDITIONAL_WEB_UPDATE_LEAGUE_TYPES
        = BaseLeague.LeagueType.values();

    private final Map<Region, InstantVar> discoveryInstants = new HashMap<>();
    private final Map<Region, InstantVar> scanInstants = new EnumMap<>(Region.class);
    private final Map<Region, InstantVar> additionalWebScanInstants = new EnumMap<>(Region.class);
    private CollectionVar<Set<Region>, Region> profileLadderWebRegions;
    private CollectionVar<Set<Region>, Region> discoveryWebRegions;

    private final PendingLadderData pendingLadderData = new PendingLadderData();
    private final Map<Region, Integer> additionalWebUpdates = new EnumMap<>(Region.class);

    @Autowired @Lazy
    private AlternativeLadderService alternativeLadderService;

    private final BlizzardSC2API api;
    private final LeagueDAO leagueDao;
    private final LeagueTierDAO leagueTierDao;
    private final DivisionDAO divisionDao;
    private final FastTeamDAO fastTeamDAO;
    private final TeamDAO teamDao;
    private final AccountDAO accountDAO;
    private final PlayerCharacterDAO playerCharacterDao;
    private final TeamMemberDAO teamMemberDao;
    private final BlizzardDAO blizzardDAO;
    private final VarDAO varDAO;
    private final SC2WebServiceUtil sc2WebServiceUtil;
    private final ConversionService conversionService;
    private final ExecutorService dbExecutorService;
    private final ClanService clanService;
    private final Predicate<BlizzardProfileTeam> teamValidationPredicate;

    @Value("${com.nephest.battlenet.sc2.ladder.alternative.web.auto:#{'false'}}")
    private boolean autoWeb;
    private boolean separateWebQueue = false;

    @Autowired
    public AlternativeLadderService
    (
        BlizzardSC2API api,
        LeagueDAO leagueDao,
        LeagueTierDAO leagueTierDao,
        DivisionDAO divisionDao,
        FastTeamDAO fastTeamDAO,
        TeamDAO teamDao,
        AccountDAO accountDAO,
        PlayerCharacterDAO playerCharacterDao,
        TeamMemberDAO teamMemberDao,
        BlizzardDAO blizzardDAO,
        VarDAO varDAO,
        SC2WebServiceUtil sc2WebServiceUtil,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService,
        Validator validator,
        @Qualifier("dbExecutorService") ExecutorService dbExecutorService,
        ClanService clanService
    )
    {
        this.api = api;
        this.leagueDao = leagueDao;
        this.leagueTierDao = leagueTierDao;
        this.divisionDao = divisionDao;
        this.fastTeamDAO = fastTeamDAO;
        this.teamDao = teamDao;
        this.accountDAO = accountDAO;
        this.playerCharacterDao = playerCharacterDao;
        this.teamMemberDao = teamMemberDao;
        this.blizzardDAO = blizzardDAO;
        this.varDAO = varDAO;
        this.sc2WebServiceUtil = sc2WebServiceUtil;
        this.conversionService = conversionService;
        this.teamValidationPredicate = DAOUtils.beanValidationPredicate(validator);
        this.dbExecutorService = dbExecutorService;
        this.clanService = clanService;
    }

    public static final int ALTERNATIVE_LADDER_ERROR_THRESHOLD = 100;
    public static final int LEGACY_LADDER_BATCH_SIZE = 500;
    public static final int ALTERNATIVE_LADDER_WEB_ERROR_THRESHOLD = 50;
    public static final int LEGACY_LADDER_WEB_BATCH_SIZE = 200;
    public static final int CONTINUE_SEASON_DISCOVERY_BATCH_SIZE = 25;
    public static final int CONTINUE_SEASON_DISCOVERY_LADDER_OFFSET = 3;
    public static final BaseLeagueTier.LeagueTierType ALTERNATIVE_TIER = BaseLeagueTier.LeagueTierType.FIRST;

    @PostConstruct
    public void init()
    {
        if(autoWeb) LOG.warn("Auto web API is active");
        for(Region region : Region.values()) additionalWebUpdates.put(region, 0);
        //catch exceptions to allow service autowiring for tests
        try {
            for(Region region : Region.values())
            {
                discoveryInstants.put(region, new InstantVar(varDAO, region.getId() + ".ladder.alternative.discovered"));
                scanInstants.put(region, new InstantVar(varDAO, region.getId() + ".ladder.alternative.updated"));
                additionalWebScanInstants.put(region, new InstantVar(varDAO, region.getId() + ".ladder.alternative.web.additional"));
            }
        }
        catch(RuntimeException ex) {
            LOG.warn(ex.getMessage(), ex);
        }
        profileLadderWebRegions = WebServiceUtil.loadRegionSetVar(varDAO, "ladder.alternative.profile.web.regions",
            "Loaded web regions for alternative profile ladders: {}");
        discoveryWebRegions = WebServiceUtil.loadRegionSetVar(varDAO, "ladder.alternative.discovery.web.regions",
            "Loaded web regions for alternative ladder discovery: {}");
    }

    public boolean isAutoWeb(Region region)
    {
        return autoWeb
            && (api.getForceRegion(region) == null
                || api.getErrorRate(api.getForceRegion(region), false) > WEB_API_FORCE_REGION_ERROR_RATE_THRESHOLD)
            && api.getErrorRate(region, false) > WEB_API_ERROR_RATE_THRESHOLD
            && api.getErrorRate(region, true) <= WEB_API_ERROR_RATE_THRESHOLD;
    }

    public boolean isProfileLadderWebRegion(Region region)
    {
        return isAutoWeb(region) || profileLadderWebRegions.getValue().contains(region);
    }

    public void addProfileLadderWebRegion(Region region)
    {
        if(profileLadderWebRegions.getValue().add(region)) profileLadderWebRegions.save();
    }

    public void removeProfileLadderWebRegion(Region region)
    {
        if(profileLadderWebRegions.getValue().remove(region)) profileLadderWebRegions.save();
    }

    public boolean isDiscoveryWebRegion(Region region)
    {
        return isAutoWeb(region) || discoveryWebRegions.getValue().contains(region);
    }

    public void addDiscoveryWebRegion(Region region)
    {
        if(discoveryWebRegions.getValue().add(region)) discoveryWebRegions.save();
    }

    public void removeDiscoveryWebRegion(Region region)
    {
        if(discoveryWebRegions.getValue().remove(region)) discoveryWebRegions.save();
    }

    public boolean isSeparateWebQueue()
    {
        return separateWebQueue;
    }

    public void setSeparateWebQueue(boolean separateWebQueue)
    {
        this.separateWebQueue = separateWebQueue;
    }

    public List<Future<Void>> updateSeason(Season season, QueueType[] queueTypes, BaseLeague.LeagueType[] leagues)
    {
        LOG.debug("Updating season {}", season);
        Instant discoveryInstant = discoveryInstants.get(season.getRegion()).getValue();
        if(discoveryInstant == null
            || System.currentTimeMillis() - discoveryInstant.toEpochMilli() >= DISCOVERY_TIME_FRAME.toMillis())
        {
            return discoverSeason(season, isProfileLadderWebRegion(season.getRegion()));
        }

        List<Future<Void>> tasks = updateOrAdditionalWebUpdate(season, queueTypes, leagues);
        tasks.addAll(continueSeasonDiscovery(season));
        return tasks;
    }

    private boolean isAdditionalWebUpdate(Region region)
    {
        Instant discoveryInstant = discoveryInstants.get(region).getValue();
        Instant scanInstant = scanInstants.get(region).getValue();
        Instant additionalScanInstant = additionalWebScanInstants.get(region).getValue();
        return isProfileLadderWebRegion(region)
            && (discoveryInstant != null
                && System.currentTimeMillis() - discoveryInstant.toEpochMilli()
                    >= ADDITIONAL_WEB_SCAN_TIME_FRAME.toMillis())
            && (additionalScanInstant == null
                || System.currentTimeMillis() - additionalScanInstant.toEpochMilli()
                    >= ADDITIONAL_WEB_SCAN_TIME_FRAME.toMillis())
            && (profileLadderWebRegions.getValue().contains(region)
                || (
                    scanInstant != null
                    && System.currentTimeMillis() - scanInstant.toEpochMilli()
                        < NON_WEB_SCAN_TIME_FRAME.toMillis()
                )
            );
    }

    private boolean isBigAdditionalWebUpdate(Region region)
    {
        return additionalWebUpdates.get(region) >= ADDITIONAL_WEB_UPDATES_PER_CYCLE;
    }

    public Pair<QueueType[], BaseLeague.LeagueType[]> getUpdateInfo
    (
        Season season,
        QueueType[] queueTypes,
        BaseLeague.LeagueType[] leagues
    )
    {
        QueueType[] resultQueues;
        BaseLeague.LeagueType[] resultLeagues;
        if(isSeparateWebQueue() && isAdditionalWebUpdate(season.getRegion()))
        {
            if(isBigAdditionalWebUpdate(season.getRegion()))
            {
                resultQueues = BIG_ADDITIONAL_WEB_UPDATE_QUEUE_TYPES;
                resultLeagues = BIG_ADDITIONAL_WEB_UPDATE_LEAGUE_TYPES;
            }
            else
            {
                resultQueues = ADDITIONAL_WEB_UPDATE_QUEUE_TYPES;
                resultLeagues = ADDITIONAL_WEB_UPDATE_LEAGUE_TYPES;
            }
        }
        else
        {
            resultQueues = queueTypes;
            resultLeagues = leagues;
        }
        return new ImmutablePair<>(resultQueues, resultLeagues);
    }

    public List<Tuple3<Region, BlizzardPlayerCharacter[], Long>> getExistingLadderIds
    (Season season, QueueType[] queueTypes, BaseLeague.LeagueType[] leagues)
    {
        return blizzardDAO.findLegacyLadderIds
        (
            season.getBattlenetId(),
            new Region[]{season.getRegion()},
            queueTypes,
            leagues,
            BlizzardSC2API.PROFILE_LADDER_RETRY_COUNT
        );
    }

    private List<Future<Void>> updateOrAdditionalWebUpdate(Season season, QueueType[] queueTypes, BaseLeague.LeagueType[] leagues)
    {
        Pair<QueueType[], BaseLeague.LeagueType[]> queuesLeagues
            = getUpdateInfo(season, queueTypes, leagues);
        List<Tuple3<Region, BlizzardPlayerCharacter[], Long>> profileLadderIds =
            getExistingLadderIds(season, queuesLeagues.getLeft(), queuesLeagues.getRight());
        List<Future<Void>> tasks;
        if(isAdditionalWebUpdate(season.getRegion()))
        {
            boolean big = isBigAdditionalWebUpdate(season.getRegion());
            LOG.info
            (
                "Starting additional web update. {}, {}, web queue: {}",
                season.getRegion(),
                big ? "big" : "small",
                isSeparateWebQueue()
            );
            tasks = updateLadders(season, Set.of(queuesLeagues.getLeft()), profileLadderIds, true);
            additionalWebScanInstants.get(season.getRegion()).setValueAndSave(Instant.now());
            additionalWebUpdates.put
            (
                season.getRegion(),
                big ? 0 : additionalWebUpdates.get(season.getRegion()) + 1
            );
        }
        else
        {
            tasks = updateLadders(season, Set.of(queueTypes), profileLadderIds, false);
            scanInstants.get(season.getRegion()).setValueAndSave(Instant.now());
        }
        return tasks;
    }

    public void updateThenSmartDiscoverSeason(Season season, QueueType[] queueTypes, BaseLeague.LeagueType[] leagues)
    {
        int divisionCount = divisionDao.getDivisionCount(season.getBattlenetId(), season.getRegion(), leagues, QueueType.LOTV_1V1, TeamType.ARRANGED);
        if(divisionCount < SMART_DISCOVERY_MAX.get(season.getRegion()))
        {
            updateThenContinueDiscoverSeason(season, queueTypes, leagues);
        }
        else
        {
            updateSeason(season, queueTypes, leagues);
        }
    }

    public List<Future<Void>> discoverSeason(Season season, boolean web)
    {
        long lastDivision = divisionDao.findLastDivision(season.getBattlenetId() - 1, season.getRegion())
            .orElse(BlizzardSC2API.LAST_LADDER_IDS.get(season.getRegion())) + 1;
        return discoverSeason(season, lastDivision, web, null);
    }

    private long getLastDivision(Season season)
    {
        return divisionDao
            .findLastDivision(season.getBattlenetId(), season.getRegion())
            .orElseGet(()->divisionDao
                .findLastDivision(season.getBattlenetId() - 1, season.getRegion())
                .orElse(BlizzardSC2API.LAST_LADDER_IDS.get(season.getRegion()))) + 1;
    }

    private List<Future<Void>> continueSeasonDiscovery(Season season)
    {
        long lastDivision = getLastDivision(season) - CONTINUE_SEASON_DISCOVERY_LADDER_OFFSET;
        return discoverSeason(season, lastDivision, isDiscoveryWebRegion(season.getRegion()), CONTINUE_SEASON_DISCOVERY_BATCH_SIZE);
    }

    public void updateThenContinueDiscoverSeason(Season season, QueueType[] queueTypes, BaseLeague.LeagueType[] leagues)
    {
        updateSeason(season, queueTypes, leagues);
        continueSeasonDiscovery(season);
    }

    private List<Future<Void>> discoverSeason
    (
        Season season,
        long lastDivision,
        boolean web,
        @Nullable Integer batchSize
    )
    {
        LOG.info("Discovering {} ladders", season);

        List<Tuple3<Region, BlizzardPlayerCharacter[], Long>> profileIds
            = getProfileLadderIds(season, lastDivision, batchSize);
        LOG.info("{} {} ladders found", profileIds.size(), season);
        List<Future<Void>> tasks =
            updateLadders(season, QueueType.getTypes(StatsService.VERSION), profileIds, web);
        discoveryInstants.get(season.getRegion()).setValueAndSave(Instant.now());
        return tasks;
    }

    private List<Future<Void>> updateLadders
    (
        Season season,
        Set<QueueType> queueTypes,
        List<Tuple3<Region, BlizzardPlayerCharacter[], Long>> ladders,
        boolean web
    )
    {
        if(web) LOG.warn("Using web API for {}", season);
        List<Future<Void>> dbTasks = new ArrayList<>();
        (web ? api.getProfileLadders(ladders, queueTypes, true) : api.getProfileLadders(ladders, queueTypes))
            .buffer(LADDER_BATCH_SIZE)
            .toStream()
            .forEach((r)->dbTasks.add(dbExecutorService.submit(()->alternativeLadderService.saveProfileLadders(season, r), null)));
        return dbTasks;
    }

    private List<Tuple3<Region, BlizzardPlayerCharacter[], Long>> getProfileLadderIds
    (Season season, long lastDivision, @Nullable Integer batchSize)
    {
        boolean webDiscovery = isDiscoveryWebRegion(season.getRegion());
        if(webDiscovery) LOG.warn("Using web API for ladder discovery for {}", season);
        int errorThreshold = webDiscovery ? ALTERNATIVE_LADDER_WEB_ERROR_THRESHOLD : ALTERNATIVE_LADDER_ERROR_THRESHOLD;
        batchSize = batchSize != null
            ? batchSize
            : webDiscovery ? LEGACY_LADDER_WEB_BATCH_SIZE : LEGACY_LADDER_BATCH_SIZE;
        List<Tuple3<Region, BlizzardPlayerCharacter[], Long>> profileLadderIds = new ArrayList<>();
        AtomicInteger discovered = new AtomicInteger(1);
        while(discovered.get() > 0)
        {
            discovered.set(0);
            api.getProfileLadderIds
            (
                season.getRegion(),
                lastDivision,
                lastDivision + batchSize,
                webDiscovery
            )
                .toStream()
                .forEach((id)->{
                    profileLadderIds.add(id);
                    discovered.getAndIncrement();
                    LOG.debug("Ladder discovered: {} {}", id.getT1(), id.getT3());
                });
            if(batchSize - discovered.get() > errorThreshold) break;
            lastDivision+=batchSize;
        }
        Season seasonAfterScan = Season.of(sc2WebServiceUtil.getCurrentOrLastOrExistingSeason(season.getRegion()), season.getRegion());
        if(!Objects.equals(season.getBattlenetId(), seasonAfterScan.getBattlenetId()))
            throw new IllegalStateException("Season changed when ladder discovery was in progress");
        return profileLadderIds;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveProfileLadders
    (Season season, List<Tuple2<BlizzardProfileLadder, Tuple3<Region, BlizzardPlayerCharacter[], Long>>> ids)
    {
        for(Tuple2<BlizzardProfileLadder, Tuple3<Region, BlizzardPlayerCharacter[], Long>> id : ids)
            updateTeams(season, id.getT2(), id.getT1());
    }

    private void updateTeams(Season season, Tuple3<Region, BlizzardPlayerCharacter[], Long> id, BlizzardProfileLadder ladder)
    {
        int teamMemberCount = ladder.getLeague().getQueueType().getTeamFormat()
            .getMemberCount(ladder.getLeague().getTeamType());
        int ladderMemberCount = ladder.getLadderTeams().length * teamMemberCount;
        BaseLeague baseLeague = ladder.getLeague();
        Division division = getOrCreateDivision(season, ladder.getLeague(), id.getT3());
        Set<TeamMember> members = new HashSet<>(ladderMemberCount, 1.0F);
        Set<PlayerCharacter> characters = new HashSet<>();
        List<Pair<PlayerCharacter, Clan>> clans = new ArrayList<>();
        List<AlternativeTeamData> newTeams = new ArrayList<>();
        List<Tuple2<Team, BlizzardProfileTeam>> validTeams = Arrays.stream(ladder.getLadderTeams())
            .filter(teamValidationPredicate.and(t->isValidTeam(t, teamMemberCount, ladder.getLeague().getQueueType().getTeamFormat())))
            .map
            (
                bTeam->Tuples.of
                (
                    new Team
                    (
                        null,
                        season.getBattlenetId(), season.getRegion(),
                        baseLeague, null,
                        teamDao.legacyIdOf(baseLeague, bTeam), division.getId(),
                        bTeam.getRating(), bTeam.getWins(), bTeam.getLosses(), 0, bTeam.getPoints(),
                        OffsetDateTime.now()
                    ),
                    bTeam
                )
            )
            .collect(Collectors.toList());
        Team[] changedTeams = fastTeamDAO
            .merge(validTeams.stream().map(Tuple2::getT1).toArray(Team[]::new));
        teamDao.merge(changedTeams);
        validTeams.stream()
            .filter(t->t.getT1().getId() != null)
            .forEach(t->extractTeamData(season, t.getT1(), t.getT2(), newTeams, characters, clans, members));
        saveNewCharacterData(newTeams, members);
        savePlayerCharacters(characters);
        teamMemberDao.merge(members.toArray(TeamMember[]::new));
        clanService.saveClans(clans);
        pendingLadderData.getCharacters().addAll(characters);
        pendingLadderData.getCharacters().addAll
        (
            newTeams.stream()
                .map(AlternativeTeamData::getCharacter)
                .collect(Collectors.toList())
        );
        LOG.debug
        (
            "Ladder saved: {} {} {}({}/{} teams)",
            id.getT1(), id.getT3(), ladder.getLeague(),
            changedTeams.length, validTeams.size()
        );
    }

    private void extractTeamData
    (
        Season season,
        Team team,
        BlizzardProfileTeam bTeam,
        List<AlternativeTeamData> newTeams,
        Set<PlayerCharacter> characters,
        List<Pair<PlayerCharacter, Clan>> clans,
        Set<TeamMember> members
    )
    {
        pendingLadderData.getTeams().add(team.getId());
        for(BlizzardProfileTeamMember bMember : bTeam.getTeamMembers())
        {
            PlayerCharacter playerCharacter = playerCharacterDao.find(season.getRegion(), bMember.getRealm(), bMember.getId())
                .orElse(null);

            if(playerCharacter == null) {
                addNewAlternativeCharacter(season, team, bMember, newTeams, clans);
            } else {
                addExistingAlternativeCharacter(team, bTeam, playerCharacter, bMember, characters, members, clans);
            }
        }
    }


    // This creates fake accounts for new alternative characters. The main update method will override them with the
    // real ones
    private void addNewAlternativeCharacter
    (
        Season season,
        Team team,
        BlizzardProfileTeamMember bMember,
        List<AlternativeTeamData> newTeams,
        List<Pair<PlayerCharacter, Clan>> clans
    )
    {
        String fakeBtag = BasePlayerCharacter.DEFAULT_FAKE_NAME + "#"
            + conversionService.convert(season.getRegion(), Integer.class)
            + bMember.getRealm()
            + bMember.getId();
        Account fakeAccount = new Account(null, Partition.of(season.getRegion()), fakeBtag);
        PlayerCharacter character = PlayerCharacter.of(fakeAccount, season.getRegion(), bMember);
        clans.add(extractClan(character, bMember));
        newTeams.add(new AlternativeTeamData(fakeAccount, character, team, bMember.getFavoriteRace()));
    }

    private void addExistingAlternativeCharacter
    (
        Team team,
        BlizzardProfileTeam bTeam,
        PlayerCharacter playerCharacter,
        BlizzardProfileTeamMember bMember,
        Set<PlayerCharacter> characters,
        Set<TeamMember> members,
        List<Pair<PlayerCharacter, Clan>> clans
    )
    {
        clans.add(extractClan(playerCharacter, bMember));

        playerCharacter.setName(bMember.getName());
        characters.add(playerCharacter);

        TeamMember member = new TeamMember(team.getId(), playerCharacter.getId(), null, null, null, null);
        if(bMember.getFavoriteRace() != null)
            member.setGamesPlayed(bMember.getFavoriteRace(), bTeam.getWins() + bTeam.getLosses());
        members.add(member);
    }

    public static Pair<PlayerCharacter, Clan> extractClan
    (
        PlayerCharacter playerCharacter,
        BlizzardProfileTeamMember bMember
    )
    {
        return new ImmutablePair<>
        (
            playerCharacter,
            bMember.getClanTag() != null
                ? Clan.of(bMember.getClanTag(), playerCharacter.getRegion())
                : null
        );
    }

    //this ensures the consistent order for concurrent entities(accounts and players)
    private void saveNewCharacterData
    (List<AlternativeTeamData> newTeams, Set<TeamMember> teamMembers)
    {
        if(newTeams.size() == 0) return;

        newTeams.sort(Comparator.comparing(AlternativeTeamData::getAccount, Account.NATURAL_ID_COMPARATOR));
        for(AlternativeTeamData curMembers : newTeams)
            accountDAO.merge(curMembers.getAccount(), curMembers.getCharacter());

        newTeams.sort(Comparator.comparing(AlternativeTeamData::getCharacter, PlayerCharacter.NATURAL_ID_COMPARATOR));
        for(AlternativeTeamData curNewTeam : newTeams)
        {
            Account account = curNewTeam.getAccount();

            curNewTeam.getCharacter().setAccountId(account.getId());
            PlayerCharacter character = playerCharacterDao.merge(curNewTeam.getCharacter());

            Team team = curNewTeam.getTeam();
            TeamMember teamMember = new TeamMember(team.getId(), character.getId(), null, null, null, null);
            if(curNewTeam.getRace() != null)
                teamMember.setGamesPlayed(curNewTeam.getRace(), team.getWins() + team.getLosses() + team.getTies());
            teamMembers.add(teamMember);
        }
    }

    //this ensures the consistent order for concurrent entities
    private void savePlayerCharacters(Set<PlayerCharacter> characters)
    {
        if(characters.isEmpty()) return;

        characters.stream().sorted(PlayerCharacter.NATURAL_ID_COMPARATOR).forEach(playerCharacterDao::merge);
    }

    public Division getOrCreateDivision
    (Season season, BaseLeague bLeague, long battlenetId)
    {
        return divisionDao
            .findDivision(season.getBattlenetId(), season.getRegion(), bLeague.getQueueType(), bLeague.getTeamType(), battlenetId)
            .orElseGet(()-> createDivision(season, bLeague, battlenetId));
    }

    private Division createDivision
    (Season season, BaseLeague bLeague, long battlenetId)
    {
        LeagueTier tier = alternativeLadderService.createLeagueTier(season, bLeague);
        return divisionDao.merge(new Division(null, tier.getId(), battlenetId));
    }

    @Cacheable(cacheNames = "fqdn-ladder-scan", keyGenerator = "fqdnSimpleKeyGenerator")
    public LeagueTier createLeagueTier(Season season, BaseLeague bLeague)
    {
        return leagueTierDao.findByLadder(
            season.getBattlenetId(), season.getRegion(), bLeague.getType(), bLeague.getQueueType(), bLeague.getTeamType(), ALTERNATIVE_TIER)
            .orElseGet(()->{
                League league = leagueDao
                    .merge(new League(null, season.getId(), bLeague.getType(), bLeague.getQueueType(), bLeague.getTeamType()));
                return leagueTierDao.merge(new LeagueTier(null, league.getId(), ALTERNATIVE_TIER, 0, 0));
            });
    }

    private boolean isValidTeam(BlizzardProfileTeam team, int expectedMemberCount, TeamFormat teamFormat)
    {
        /*
            empty teams are messing with the stats numbers
            there are ~0.1% of partial teams, which is a number low enough to consider such teams invalid
            this probably has something to do with players revoking their information from blizzard services
         */
        return team.getTeamMembers().length == expectedMemberCount
            //a team can have 0 games while a team member can have some games played, which is clearly invalid
            && (team.getWins() > 0 || team.getLosses() > 0 || team.getTies() > 0)
            //1v1 members *MUST* have a favorite race
            && (teamFormat != TeamFormat._1V1 || team.getTeamMembers()[0].getFavoriteRace() != null);
        }

    protected PendingLadderData copyAndClearPendingData()
    {
        PendingLadderData pending = new PendingLadderData(pendingLadderData);
        pendingLadderData.clear();
        return pending;
    }

}
