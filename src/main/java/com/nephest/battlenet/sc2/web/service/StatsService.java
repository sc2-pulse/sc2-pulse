// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.Version;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLadder;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLadderLeagueKey;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLeague;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLeagueTier;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardPlayerCharacter;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardSeason;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTeam;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTeamMember;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTierDivision;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.Division;
import com.nephest.battlenet.sc2.model.local.InstantVar;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.LeagueTier;
import com.nephest.battlenet.sc2.model.local.LongVar;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.TeamMember;
import com.nephest.battlenet.sc2.model.local.Var;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.DAOUtils;
import com.nephest.battlenet.sc2.model.local.dao.DivisionDAO;
import com.nephest.battlenet.sc2.model.local.dao.FastTeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.LeagueDAO;
import com.nephest.battlenet.sc2.model.local.dao.LeagueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.LeagueTierDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.PopulationStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.QueueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.service.EventService;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Validator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuple5;
import reactor.util.function.Tuples;

@Service
public class StatsService
{

    private static final Logger LOG = LoggerFactory.getLogger(StatsService.class);

    public static final Version VERSION = Version.LOTV;
    public static final int STALE_LADDER_TOLERANCE = 1;
    public static final int STALE_LADDER_DEPTH = 12;
    public static final int LADDER_BATCH_SIZE = 100;
    public static final Duration FORCED_LADDER_SCAN_FRAME = Duration.ofHours(2);
    /*
        Disable partial updates because alternative ladder service should be fast enough
        now. Might want to reactivate it later if anything goes wrong, so leaving this note just in
        case.
     */
    public static final int PARTIAL_ALTERNATIVE_UPDATE_REGION_THRESHOLD = Integer.MAX_VALUE;
    public static final Set<BaseLeague.LeagueType> PARTIAL_UPDATE_MAIN_LEAGUES =
        Collections.unmodifiableSet(EnumSet.of
        (
            BaseLeague.LeagueType.BRONZE,
            //BaseLeague.LeagueType.SILVER,
            BaseLeague.LeagueType.GOLD,
            BaseLeague.LeagueType.PLATINUM,
            BaseLeague.LeagueType.DIAMOND,
            BaseLeague.LeagueType.MASTER,
            BaseLeague.LeagueType.GRANDMASTER
        ));
    public static final List<Map<QueueType, Set<BaseLeague.LeagueType>>> PARTIAL_UPDATE_DATA =
        List.of
        (
            Map.of(QueueType.LOTV_1V1, LadderUpdateContext.ALL_LEAGUES),
            Map.of
            (
                QueueType.LOTV_1V1, PARTIAL_UPDATE_MAIN_LEAGUES,
                QueueType.LOTV_2V2,
                Set.of
                (
                    BaseLeague.LeagueType.BRONZE,
                    BaseLeague.LeagueType.SILVER,
                    BaseLeague.LeagueType.GOLD
                )
            ),
            Map.of
            (
                QueueType.LOTV_1V1, PARTIAL_UPDATE_MAIN_LEAGUES,
                QueueType.LOTV_2V2,
                Set.of
                (
                    BaseLeague.LeagueType.PLATINUM,
                    BaseLeague.LeagueType.DIAMOND,
                    BaseLeague.LeagueType.MASTER,
                    BaseLeague.LeagueType.GRANDMASTER
                )
            ),
            Map.of
            (
                QueueType.LOTV_1V1, PARTIAL_UPDATE_MAIN_LEAGUES,
                QueueType.LOTV_3V3, LadderUpdateContext.ALL_LEAGUES
            ),
            Map.of
            (
                QueueType.LOTV_1V1, PARTIAL_UPDATE_MAIN_LEAGUES,
                QueueType.LOTV_4V4, LadderUpdateContext.ALL_LEAGUES,
                QueueType.LOTV_ARCHON, LadderUpdateContext.ALL_LEAGUES
            )
        );
    public static final Duration STALE_DATA_TEAM_STATES_DEPTH = Duration.ofMinutes(45);
    public static final Duration FORCED_ALTERNATIVE_UPDATE_DURATION = Duration.ofDays(7);

    @Autowired @Lazy
    private StatsService statsService;

    private final Set<Region> alternativeRegions = new HashSet<>();

    @Value("${com.nephest.battlenet.sc2.ladder.alternative.regions:#{''}}")
    private final Set<Region> forcedAlternativeRegions = new HashSet<>();

    @Value("${com.nephest.battlenet.sc2.ladder.forceUpdate:#{'false'}}")
    private boolean forceUpdate;

    private final Map<Region, Set<Long>> failedLadders = new EnumMap<>(Region.class);
    private final Map<Region, InstantVar> forcedUpdateInstants = new EnumMap<>(Region.class);
    private final Map<Region, InstantVar> forcedAlternativeUpdateInstants = new EnumMap<>(Region.class);
    private final Map<Region, LongVar> partialUpdates = new EnumMap<>(Region.class);
    private final Map<Region, LongVar> partialUpdateIndexes = new EnumMap<>(Region.class);
    private final PendingLadderData pendingLadderData = new PendingLadderData();

    private AlternativeLadderService alternativeLadderService;
    private BlizzardSC2API api;
    private SeasonDAO seasonDao;
    private LeagueDAO leagueDao;
    private LeagueTierDAO leagueTierDao;
    private DivisionDAO divisionDao;
    private TeamDAO teamDao;
    private FastTeamDAO fastTeamDAO;
    private TeamStateDAO teamStateDAO;
    private AccountDAO accountDao;
    private PlayerCharacterDAO playerCharacterDao;
    private TeamMemberDAO teamMemberDao;
    private QueueStatsDAO queueStatsDAO;
    private LeagueStatsDAO leagueStatsDao;
    private PlayerCharacterStatsDAO playerCharacterStatsDAO;
    private PopulationStateDAO populationStateDAO;
    private VarDAO varDAO;
    private SC2WebServiceUtil sc2WebServiceUtil;
    private ConversionService conversionService;
    private ExecutorService dbExecutorService;
    private ClanService clanService;
    private EventService eventService;
    private Predicate<BlizzardTeam> teamValidationPredicate;
    private final Map<Region, Map<QueueType, Map<BaseLeague.LeagueType, Instant>>> updateInstants =
        Arrays.stream(Region.values())
            .collect(Collectors.toUnmodifiableMap
            (
                Function.identity(),
                r->QueueType.getTypes(VERSION).stream()
                    .collect(Collectors.toUnmodifiableMap
                    (
                        Function.identity(),
                        q->Arrays.stream(BaseLeague.LeagueType.values())
                            .collect(Collectors.toMap
                            (
                                Function.identity(),
                                l->Instant.now().minus(Duration.ofDays(1))
                            ))
                    ))
            ));

    public StatsService(){}

    @Autowired
    public StatsService
    (
        AlternativeLadderService alternativeLadderService,
        BlizzardSC2API api,
        SeasonDAO seasonDao,
        LeagueDAO leagueDao,
        LeagueTierDAO leagueTierDao,
        DivisionDAO divisionDao,
        TeamDAO teamDao,
        FastTeamDAO fastTeamDAO,
        TeamStateDAO teamStateDAO,
        AccountDAO accountDao,
        PlayerCharacterDAO playerCharacterDao,
        TeamMemberDAO teamMemberDao,
        QueueStatsDAO queueStatsDAO,
        LeagueStatsDAO leagueStatsDao,
        PlayerCharacterStatsDAO playerCharacterStatsDAO,
        PopulationStateDAO populationStateDAO,
        VarDAO varDAO,
        SC2WebServiceUtil sc2WebServiceUtil,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService,
        Validator validator,
        @Qualifier("dbExecutorService") ExecutorService dbExecutorService,
        ClanService clanService,
        EventService eventService
    )
    {
        this.alternativeLadderService = alternativeLadderService;
        this.api = api;
        this.seasonDao = seasonDao;
        this.leagueDao = leagueDao;
        this.leagueTierDao = leagueTierDao;
        this.divisionDao = divisionDao;
        this.teamDao = teamDao;
        this.fastTeamDAO = fastTeamDAO;
        this.teamStateDAO = teamStateDAO;
        this.accountDao = accountDao;
        this.playerCharacterDao = playerCharacterDao;
        this.teamMemberDao = teamMemberDao;
        this.queueStatsDAO = queueStatsDAO;
        this.leagueStatsDao = leagueStatsDao;
        this.playerCharacterStatsDAO = playerCharacterStatsDAO;
        this.populationStateDAO = populationStateDAO;
        this.varDAO = varDAO;
        this.sc2WebServiceUtil = sc2WebServiceUtil;
        this.conversionService = conversionService;
        this.dbExecutorService = dbExecutorService;
        this.clanService = clanService;
        this.eventService = eventService;
        this.teamValidationPredicate = DAOUtils.beanValidationPredicate(validator);
        for(Region r : Region.values())
            failedLadders.put(r, ConcurrentHashMap.newKeySet());
    }

    @PostConstruct
    public void init()
    {
        for(Region region : Region.values())
        {
            forcedUpdateInstants.put(region, new InstantVar(varDAO, region.getId() + ".ladder.updated.forced", false));
            forcedAlternativeUpdateInstants.put(region, new InstantVar(varDAO, region.getId() + ".ladder.alternative.forced.timestamp", false));
            partialUpdates.put(region, new LongVar(varDAO, region.getId() + ".ladder.partial", false));
            partialUpdateIndexes.put(region, new LongVar(varDAO, region.getId() + ".ladder.partial.ix", false));
        }
        //catch exceptions to allow service autowiring for tests
        try {
            loadAlternativeRegions();
            loadForcedAlternativeRegions();
            Stream.of
            (
                forcedUpdateInstants.values().stream(),
                forcedAlternativeUpdateInstants.values().stream(),
                partialUpdates.values().stream(),
                partialUpdateIndexes.values().stream()
            )
                .flatMap(Function.identity())
                .map(var->(Var<?>) var)
                .forEach(Var::load);
            partialUpdateIndexes.values().stream()
                .filter(v->v.getValue() == null)
                .forEach(v->v.setValueAndSave(0L));
        }
        catch(RuntimeException ex) {
            LOG.warn(ex.getMessage(), ex);
        }
    }

    protected void setNestedService(StatsService statsService)
    {
        this.statsService = statsService;
    }

    public Set<Region> getAlternativeRegions()
    {
        return alternativeRegions;
    }

    public Set<Region> getForcedAlternativeRegions()
    {
        return Collections.unmodifiableSet(forcedAlternativeRegions);
    }

    protected Map<Region, InstantVar> getForcedAlternativeUpdateInstants()
    {
        return forcedAlternativeUpdateInstants;
    }

    @CacheEvict(cacheNames="fqdn-ladder-scan", allEntries=true)
    public void updateAll(Map<Region, Map<QueueType, Set<BaseLeague.LeagueType>>> data)
    {
        long start = System.currentTimeMillis();
        int lastSeasonIx = api.getLastSeason(Region.EU, seasonDao.getMaxBattlenetId()).block().getId() + 1;
        for(int season = BlizzardSC2API.FIRST_SEASON; season < lastSeasonIx; season++)
        {
            updateSeason(season, data);
            LOG.info("Updated season {}", season);
        }
        teamStateDAO.removeExpired();
        playerCharacterStatsDAO.mergeCalculate();

        long seconds = (System.currentTimeMillis() - start) / 1000;
        LOG.info("Updated all after {} seconds", seconds);
    }

    @CacheEvict(cacheNames="fqdn-ladder-scan", allEntries=true)
    public Map<Region, LadderUpdateTaskContext<Void>> updateCurrent
    (
        Map<Region, Map<QueueType, Set<BaseLeague.LeagueType>>> data,
        boolean allStats,
        UpdateContext updateContext
    )
    {
        Instant start = Instant.now();

        checkStaleData(data.keySet());
        Map<Region, LadderUpdateTaskContext<Void>> ctx
            = updateCurrentSeason(data, allStats, updateContext);

        updateInstants(ctx, start);
        long seconds = (System.currentTimeMillis() - start.toEpochMilli()) / 1000;
        LOG.info("Updated current for {} after {} seconds", ctx, seconds);
        return ctx;
    }

    private void updateInstants
    (
        Map<Region, LadderUpdateTaskContext<Void>> ctx,
        Instant instant
    )
    {
        for(Region region : ctx.keySet())
            for(QueueType queue : ctx.get(region).getData().keySet())
                for(BaseLeague.LeagueType league : ctx.get(region).getData().get(queue))
                    updateInstants.get(region).get(queue).put(league, instant);
    }

    public void updateLadders(int seasonId, Region region, Long[] ids)
    {
        Season season = seasonDao.merge(Season.of(api.getSeason(region, seasonId).block(), region));
        api.getLadders(region, ids)
            .toStream((int) (BlizzardSC2API.REQUESTS_PER_SECOND_CAP * 2))
            .forEach(l->statsService.saveLadder(season, l.getT1(), l.getT2(), alternativeLadderService));

    }

    @Transactional
    (
        //isolation = Isolation.READ_COMMITTED,
        propagation = Propagation.REQUIRES_NEW
    )
    public void saveLadder(Season season, BlizzardLadder bLadder, long id,AlternativeLadderService alternativeLadderService)
    {
        BlizzardLadderLeagueKey lKey = bLadder.getLeague().getLeagueKey();
        if(!lKey.getSeasonId().equals(season.getBattlenetId())) return;

        League league = new League(null, null, lKey.getLeagueId(), lKey.getQueueId(), lKey.getTeamType());
        LeagueTier tier = new LeagueTier(null, null, AlternativeLadderService.ALTERNATIVE_TIER, 0, 0);
        Division division = alternativeLadderService.getOrCreateDivision(season, lKey, id);
        updateTeams(bLadder.getTeams(), season, league, tier, division, null);
    }

    private void updateSeason
    (
        int seasonId,
        Map<Region, Map<QueueType, Set<BaseLeague.LeagueType>>> data
    )
    {
        for(Map.Entry<Region, Map<QueueType, Set<BaseLeague.LeagueType>>> e : data.entrySet())
            updateSeason(e.getKey(), seasonId, data.get(e.getKey()));
        updateSeasonStats(seasonId, true);
    }

    private PendingLadderData copyAndClearPendingData()
    {
        PendingLadderData pending = new PendingLadderData(pendingLadderData);
        pendingLadderData.clear();
        return pending;
    }

    public Future<Void> afterCurrentSeasonUpdate(boolean allStats)
    {
        PendingLadderData pending = copyAndClearPendingData();
        PendingLadderData altPending = alternativeLadderService.copyAndClearPendingData();
        return dbExecutorService.submit(()->
            statsService.afterCurrentSeasonUpdate(allStats, pending, altPending), null);
    }

    public void afterCurrentSeasonUpdate
    (
        boolean allStats,
        PendingLadderData pending,
        PendingLadderData altPending
    )
    {
        teamStateDAO.removeExpired();
        for(int season : pending.getStatsUpdates()) updateSeasonStats(season, allStats);
        takePopulationSnapshot(pending.getStatsUpdates());
        process(pending);
        process(altPending);
        eventService.createLadderUpdateEvent(allStats);
    }

    private void process(PendingLadderData pending)
    {
        processPendingCharacters(pending.getCharacters());
        LOG.info
        (
            "Created {} team snapshots",
            teamStateDAO.takeSnapshot(new ArrayList<>(pending.getTeams()))
        );
    }

    public void processPendingCharacters(Set<PlayerCharacter> pendingCharacters)
    {
        if(pendingCharacters.isEmpty()) return;

        Set<Long> pendingCharacterIds = pendingCharacters.stream()
            .map(PlayerCharacter::getId)
            .collect(Collectors.toSet());
        playerCharacterStatsDAO.mergeCalculate(pendingCharacterIds);
        PlayerCharacter[] characters = pendingCharacters.toArray(PlayerCharacter[]::new);
        eventService.createLadderCharacterActivityEvent(characters);
        pendingCharacters.clear();
        LOG.info("Created {} character ladder activity events", characters.length);
    }

    private void updateSeasonStats
    (int seasonId, boolean allStats)
    {
        if(allStats) queueStatsDAO.mergeCalculateForSeason(seasonId);
        leagueStatsDao.mergeCalculateForSeason(seasonId);
    }

    private void takePopulationSnapshot(Set<Integer> seasons)
    {
        populationStateDAO.takeSnapshot(seasons);
        for(Integer seasonId : seasons) teamDao.updateRanks(seasonId);
    }

    private void updateSeason
    (
        Region region,
        int seasonId,
        Map<QueueType, Set<BaseLeague.LeagueType>> data
    )
    {
        BlizzardSeason bSeason = api.getSeason(region, seasonId).block();
        Season season = seasonDao.merge(Season.of(bSeason, region));
        updateLeagues(bSeason, season, data, false, new UpdateContext());
        LOG.debug("Updated leagues: {} {}", seasonId, region);
    }

    private Map<Region, LadderUpdateTaskContext<Void>> updateCurrentSeason
    (
        Map<Region, Map<QueueType, Set<BaseLeague.LeagueType>>> data,
        boolean allStats,
        UpdateContext updateContext
    )
    {
        Map<Region, LadderUpdateTaskContext<Void>> ctx = new EnumMap<>(Region.class);
        //there can be two seasons here when a new season starts
        Set<Integer> seasons = new HashSet<>(2);
        for(Map.Entry<Region, Map<QueueType, Set<BaseLeague.LeagueType>>> entry : data.entrySet())
        {
            Region region = entry.getKey();
            int maxSeason = seasonDao.getMaxBattlenetId(region);
            UpdateContext regionalContext = getLadderUpdateContext(region, updateContext);
            BlizzardSeason bSeason = sc2WebServiceUtil.getCurrentOrLastOrExistingSeason(region, maxSeason);
            Season season = seasonDao.merge(Season.of(bSeason, region));
            createLeagues(season);
            ctx.put
            (
                region,
                updateOrAlternativeUpdate(bSeason, season, entry.getValue(), true, regionalContext)
            );
            seasons.add(season.getBattlenetId());
            if(regionalContext.getInternalUpdate() == null) forcedUpdateInstants.get(region).setValueAndSave(Instant.now());
            LOG.debug("Updated leagues: {} {}", season.getBattlenetId(), region);
        }
        pendingLadderData.getStatsUpdates().addAll(seasons);
        return ctx;
    }

    private LadderUpdateTaskContext<Void> updateOrAlternativeUpdate
    (
        BlizzardSeason bSeason,
        Season season,
        Map<QueueType, Set<BaseLeague.LeagueType>> data,
        boolean currentSeason,
        UpdateContext updateContext
    )
    {
        if(!isAlternativeUpdate(season.getRegion(), currentSeason))
        {
            fastTeamDAO.remove(season.getRegion());
            LOG.debug("Cleared FastTeamDAO for {}", season.getRegion());
            return update
            (
                season, data, updateContext, false,
                ctx->updateLeagues
                (
                    bSeason,
                    ctx.getSeason(),
                    ctx.getData(),
                    currentSeason,
                    updateContext
                )
            );
        }
        else
        {
            fastTeamDAO.load(season.getRegion(), season.getBattlenetId());
            LOG.debug("Loaded teams into FastTeamDAO for {}", season);
            return update
            (
                season, data, updateContext, true,
                ctx->alternativeLadderService.updateSeason(ctx.getSeason(), ctx.getData())
            );
        }
    }

    private LadderUpdateTaskContext<Void> update
    (
        Season season,
        Map<QueueType, Set<BaseLeague.LeagueType>> data,
        UpdateContext updateContext,
        boolean alternative,
        Function<LadderUpdateContext, List<Future<Void>>> updater
    )
    {
        boolean partialUpdate = alternative
            ? isPartialUpdate(season.getRegion())
            : isPartialUpdate(season.getRegion(), updateContext);
        LongVar partialUpdateIndex = partialUpdateIndexes.get(season.getRegion());
        LadderUpdateContext context = new LadderUpdateContext
        (
            season,
            partialUpdate
                ? PARTIAL_UPDATE_DATA.get(partialUpdateIndex.getValue().intValue())
                : data
        );
        if(partialUpdate) LOG.info("Partially updating {}({})", season, context.getData());
        List<Future<Void>> tasks = updater.apply(context);
        if(partialUpdate)
            partialUpdateIndex.setValueAndSave
            (
                partialUpdateIndex.getValue() == PARTIAL_UPDATE_DATA.size() - 1
                    ? 0
                    : partialUpdateIndex.getValue() + 1
            );
        return new LadderUpdateTaskContext<>(season, context.getData(), tasks);
    }

    public boolean isPartialUpdate
    (
        Region region,
        UpdateContext updateContext
    )
    {
        return updateContext.getInternalUpdate() != null
        && (
            isPartialUpdate(region)
            || Stream.concat(alternativeRegions.stream(), forcedAlternativeRegions.stream())
                .distinct()
                .count() >= PARTIAL_ALTERNATIVE_UPDATE_REGION_THRESHOLD
        );
    }

    public boolean isPartialUpdate(Region region)
    {
        return partialUpdates.get(region).getValue() != null;
    }

    public boolean isAlternativeUpdate(Region region, boolean currentSeason)
    {
        return currentSeason && (forcedAlternativeRegions.contains(region) || alternativeRegions.contains(region));
    }

    private List<Future<Void>> updateLeagues
    (
        BlizzardSeason bSeason,
        Season season,
        Map<QueueType, Set<BaseLeague.LeagueType>> data,
        boolean currentSeason,
        UpdateContext updateContext
    )
    {
        if(updateContext.getInternalUpdate() == null) LOG.info("Starting forced ladder scan: {}", season.getRegion());
        LOG.debug("Updating season {}", season);
        List<Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>> ladderIds =
            getLadderIds(getLeagueIds(bSeason, season.getRegion(), data), currentSeason);
        return updateLadders(season, ladderIds, updateContext);
    }

    private List<Future<Void>> updateLadders
    (
        Season season,
        List<Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>> ladderIds,
        UpdateContext updateContext
    )
    {
        List<Future<Void>> dbTasks = new ArrayList<>();
        Flux<Tuple2<BlizzardLadder, Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>>>
            ladders = updateContext.getInternalUpdate() == null
                ? api.getLadders(ladderIds, -1, failedLadders)
                : Flux.fromIterable
                (
                    ladderIds.stream()
                        .collect(Collectors.groupingBy(t->updateInstants
                            .get(t.getT2()).get(t.getT1().getQueueType()).get(t.getT1().getType())))
                        .entrySet()
                )
                    .flatMap(e->api.getLadders(e.getValue(), e.getKey().getEpochSecond(), failedLadders));
        ladders.buffer(LADDER_BATCH_SIZE)
            .toStream()
            .forEach(l->dbTasks.add(dbExecutorService.submit(()->statsService.saveLadders(season, l, updateContext), null)));
        return dbTasks;
    }

    @Transactional
    (
        //isolation = Isolation.READ_COMMITTED,
        propagation = Propagation.REQUIRES_NEW
    )
    public void saveLadders
    (
        Season season,
        List<Tuple2<BlizzardLadder, Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>>> ladders,
        UpdateContext updateContext
    )
    {
        for(Tuple2<BlizzardLadder, Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>> l : ladders)
        {
            League league = leagueDao.merge(League.of(season, l.getT2().getT1()));
            LeagueTier tier = leagueTierDao.merge(LeagueTier.of(league, l.getT2().getT3()));
            Division division = saveDivision(season, league, tier, l.getT2().getT4());
            //force update previously failed ladders, this will pick up all skipped teams
            Instant lastUpdatedToUse = failedLadders.get(l.getT2().getT2()).remove(l.getT2().getT4().getLadderId())
                ? null
                : updateContext.getInternalUpdate() == null
                    ? null
                    : updateInstants.get(l.getT2().getT2())
                        .get(l.getT2().getT1().getQueueType())
                        .get(l.getT2().getT1().getType());
            updateTeams(l.getT1().getTeams(), season, league, tier, division, lastUpdatedToUse);
            LOG.debug
            (
                "Ladder saved: {} {} {} {}",
                season, division.getBattlenetId(), league, lastUpdatedToUse == null ? "forced" : ""
            );
        }
    }

    public Division saveDivision
    (
        Season season,
        League league,
        LeagueTier tier,
        BlizzardTierDivision bDivision
    )
    {
        /*
            Alternative ladder update doesn't have tier info, so it creates divisions with the default tier.
            Find such divisions and update their tier.
         */
        Division division = divisionDao.findDivision(
            season.getBattlenetId(), season.getRegion(), league.getQueueType(), league.getTeamType(), bDivision.getLadderId())
            .orElseGet(()->Division.of(tier, bDivision));
        division.setTierId(tier.getId());

        return division.getId() != null ? divisionDao.mergeById(division) : divisionDao.merge(division);
    }

    protected void updateTeams
    (
        BlizzardTeam[] bTeams,
        Season season,
        League league,
        LeagueTier tier,
        Division division,
        Instant lastUpdateStart
    )
    {
        if(lastUpdateStart != null) {
            int initialSize = bTeams.length;
            bTeams = Arrays.stream(bTeams)
                .filter(t->t.getLastPlayedTimeStamp().isAfter(lastUpdateStart)).toArray(BlizzardTeam[]::new);
            LOG.debug("Saving {} out of {} {} teams", bTeams.length, initialSize, division);
        }
        int memberCount = league.getQueueType().getTeamFormat().getMemberCount(league.getTeamType());
        List<Tuple3<Account, PlayerCharacter, TeamMember>> members = new ArrayList<>(bTeams.length * memberCount);
        List<Pair<PlayerCharacter, Clan>> clans = new ArrayList<>();
        Integer curSeason = seasonDao.getMaxBattlenetId(season.getRegion()) == null
            ? 0 : seasonDao.getMaxBattlenetId(season.getRegion());
        List<Tuple2<Team, BlizzardTeam>> validTeams = Arrays.stream(bTeams)
            .filter(teamValidationPredicate.and(t->isValidTeam(t, memberCount)))
            .map(bTeam->Tuples.of(Team.of(season, league, tier, division, bTeam, teamDao), bTeam))
            .collect(Collectors.toList());
        if(validTeams.isEmpty()) return;

        teamDao.merge(validTeams.stream().map(Tuple2::getT1).toArray(Team[]::new));
        validTeams.stream()
            .filter(t->t.getT1().getId() != null)
            .forEach(t->{
                extractTeamMembers(t.getT2().getMembers(), members, clans, season, t.getT1());
                if(season.getBattlenetId().equals(curSeason))
                    pendingLadderData.getTeams().add(t.getT1().getId());
            });
        saveMembersConcurrently(members);
        clanService.saveClans(clans);
        pendingLadderData.getCharacters()
            .addAll(members.stream().map(Tuple3::getT2).collect(Collectors.toList()));
    }

    //cross field validation
    private boolean isValidTeam(BlizzardTeam team, int expectedMemberCount)
    {
        /*
            empty teams are messing with the stats numbers
            there are ~0.1% of partial teams, which is a number low enough to consider such teams invalid
            this probably has something to do with players revoking their information from blizzard services
         */
        return team.getMembers().length == expectedMemberCount
            //a team can have 0 games while a team member can have some games played, which is clearly invalid
            && (team.getWins() > 0 || team.getLosses() > 0 || team.getTies() > 0);
    }

    private void extractTeamMembers
    (
        BlizzardTeamMember[] bMembers,
        List<Tuple3<Account, PlayerCharacter, TeamMember>> members,
        List<Pair<PlayerCharacter, Clan>> clans,
        Season season,
        Team team
    )
    {
        for (BlizzardTeamMember bMember : bMembers)
        {
            //blizzard can send invalid member without account sometimes. Ignoring these entries
            if (bMember.getAccount() == null) continue;

            Account account = Account.of(bMember.getAccount(), season.getRegion());
            PlayerCharacter character = PlayerCharacter.of(account, season.getRegion(), bMember.getCharacter());
            TeamMember member = TeamMember.of(team, character, bMember.getRaces());
            clans.add(extractCharacterClanPair(bMember, character));
            members.add(Tuples.of(account, character, member));
        }
    }

    public static Pair<PlayerCharacter, Clan> extractCharacterClanPair
    (
        BlizzardTeamMember bMember,
        PlayerCharacter character
    )
    {
        return new ImmutablePair<>
        (
            character,
            bMember.getClan() != null
                ? Clan.of(bMember.getClan(), character.getRegion())
                : null
        );
    }

    //this ensures the consistent order for concurrent entities(accounts and players)
    private void saveMembersConcurrently(List<Tuple3<Account, PlayerCharacter, TeamMember>> members)
    {
        if(members.size() == 0) return;

        Set<TeamMember> teamMembers = new HashSet<>(members.size(), 1.0F);

        members.sort(Comparator.comparing(Tuple2::getT1, Account.NATURAL_ID_COMPARATOR));
        for(Tuple3<Account, PlayerCharacter, TeamMember> curMembers : members)
            accountDao.merge(curMembers.getT1(), curMembers.getT2());

        members.sort(Comparator.comparing(Tuple2::getT2, PlayerCharacter.NATURAL_ID_COMPARATOR));
        for(Tuple3<Account, PlayerCharacter, TeamMember> curMembers : members)
        {
            Account account = curMembers.getT1();

            curMembers.getT2().setAccountId(account.getId());
            PlayerCharacter character = playerCharacterDao.merge(curMembers.getT2());

            curMembers.getT3().setCharacterId(character.getId());
            teamMembers.add(curMembers.getT3());
        }

        if(teamMembers.size() > 0) teamMemberDao.merge(teamMembers.toArray(teamMembers.toArray(new TeamMember[0])));
    }



    public static List<Tuple5<Region, BlizzardSeason, BaseLeague.LeagueType, QueueType, TeamType>> getLeagueIds
    (
        BlizzardSeason bSeason,
        Region region,
        Map<QueueType, Set<BaseLeague.LeagueType>> data
    )
    {
        List<Tuple5<Region, BlizzardSeason, BaseLeague.LeagueType, QueueType, TeamType>> leagueIds = new ArrayList<>();
        for(QueueType queue : data.keySet())
        {
            for(BaseLeague.LeagueType league : data.get(queue))
            {
                for(TeamType team : TeamType.values())
                {
                    if(!BlizzardSC2API.isValidCombination(league, queue, team)) continue;

                    leagueIds.add(Tuples.of(region, bSeason, league, queue, team));
                }
            }
        }
        return leagueIds;
    }

    protected List<Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>> getLadderIds
    (Iterable<? extends Tuple5<Region, BlizzardSeason, BaseLeague.LeagueType, QueueType, TeamType>> ids, boolean cur)
    {
        List<Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>> ladderIds = new ArrayList<>();
        api.getLeagues(ids, cur)
            .flatMap(l-> Flux.fromStream(
                Arrays.stream(l.getT1().getTiers())
                    .flatMap(t-> Arrays.stream(t.getDivisions())
                        .map(d->Tuples.of(l.getT1(), l.getT2(), t, d)))))
            .toStream()
            .forEach(ladderIds::add);
        return ladderIds;
    }

    public long getMaxLadderId(BlizzardSeason bSeason, Region region)
    {
        List<Tuple5<Region, BlizzardSeason, BaseLeague.LeagueType, QueueType, TeamType>> leagueIds =
            getLeagueIds(bSeason, region, LadderUpdateContext.ALL);

        AtomicLong max = new AtomicLong(-1);
        api.getLeagues(leagueIds, true)
            .doOnNext(l->{
                long maxId = Arrays.stream(l.getT1().getTiers())
                    .flatMapToLong(t->Arrays.stream(t.getDivisions()).mapToLong(BlizzardTierDivision::getLadderId))
                    .max().orElse(-1L);
                max.getAndUpdate(c->Math.max(c, maxId));
            })
            .blockLast();
        return max.get();
    }

    public void checkStaleData(Set<Region> regions)
    {
        for(Region region : regions)
        {
            int maxSeason = seasonDao.getMaxBattlenetId(region);
            checkStaleDataByTeamStateCount(region);
            BlizzardSeason bSeason = sc2WebServiceUtil.getCurrentOrLastOrExistingSeason(region, maxSeason);
            long maxId = getMaxLadderId(bSeason, region);
            if(maxId < 0) {
                if(alternativeRegions.add(region))
                    LOG.warn("Stale data detected for {}, added this region to alternative update", region);
                continue;
            }

            chainStaleDataCheck(region, maxId + STALE_LADDER_TOLERANCE, 0).block();
        }
        saveAlternativeRegions();
    }

    public void checkStaleDataByTeamStateCount(Region region)
    {
        removeForcedAlternativeRegionIfExpired(region);
        if(teamStateDAO.getCount(region, OffsetDateTime.now().minus(STALE_DATA_TEAM_STATES_DEPTH)) == 0)
        {
            if(addForcedAlternativeRegion(region))
            {
                LOG.warn("Stale data detected for {}, added this region to forced alternative update", region);
                forcedAlternativeUpdateInstants.get(region).setValueAndSave(Instant.now());
            }
        }
    }

    public void removeForcedAlternativeRegionIfExpired(Region region)
    {
        Instant from = forcedAlternativeUpdateInstants.get(region).getValue();
        if(from == null) return;

        OffsetDateTime fromOdt = OffsetDateTime.ofInstant(from, ZoneId.systemDefault());
        if(fromOdt.isBefore(OffsetDateTime.now().minus(FORCED_ALTERNATIVE_UPDATE_DURATION)))
        {
            if(removeForcedAlternativeRegion(region))
                LOG.info("Removed {} from forced alternative update due to timeout", region);
            forcedAlternativeUpdateInstants.get(region).setValueAndSave(null);
        }
    }


    private Mono<Tuple3<Region, BlizzardPlayerCharacter[], Long>> chainStaleDataCheck(Region region, long ladderId, int count)
    {
        return Mono.defer(()->
            api.getProfileLadderId(region, ladderId, alternativeLadderService.isDiscoveryWebRegion(region))
                .doOnNext(l->{
                    if(alternativeRegions.add(region))
                        LOG.warn("Stale data detected for {}, added this region to alternative update", region);
                })
                .onErrorResume(t->{
                    int next = count + 1;
                    if(next < STALE_LADDER_DEPTH) return chainStaleDataCheck(region, ladderId + 1, next);

                    if(alternativeRegions.remove(region))
                        LOG.info("{} now returns fresh data, removed it from alternative update", region);
                    return Mono.empty();
        }));
    }

    private void loadAlternativeRegions()
    {
        String var = varDAO.find("region.alternative").orElse(null);
        if(var == null || var.isEmpty()) {
            alternativeRegions.clear();
            return;
        }

        Arrays.stream(var.split(","))
            .map(Integer::valueOf)
            .map(i->conversionService.convert(i, Region.class))
            .forEach(alternativeRegions::add);
        if(!alternativeRegions.isEmpty()) LOG.warn("Alternative regions loaded: {}", alternativeRegions);
    }

    private void saveAlternativeRegions()
    {
        String var = alternativeRegions.stream()
            .map(r->conversionService.convert(r, Integer.class))
            .map(String::valueOf)
            .collect(Collectors.joining(","));
        varDAO.merge("region.alternative", var);
    }

    public boolean addForcedAlternativeRegion(Region region)
    {
        boolean result = forcedAlternativeRegions.add(region);
        saveForcedAlternativeRegions();
        return result;
    }

    public boolean removeForcedAlternativeRegion(Region region)
    {
        boolean result = forcedAlternativeRegions.remove(region);
        saveForcedAlternativeRegions();
        return result;
    }

    private void loadForcedAlternativeRegions()
    {
        String var = varDAO.find("region.alternative.forced").orElse(null);
        if(var == null || var.isEmpty()) {
            forcedAlternativeRegions.clear();
            return;
        }

        Arrays.stream(var.split(","))
            .map(Integer::valueOf)
            .map(i->conversionService.convert(i, Region.class))
            .forEach(forcedAlternativeRegions::add);
        if(!forcedAlternativeRegions.isEmpty()) LOG.warn("Forced alternative regions loaded: {}", forcedAlternativeRegions);
    }

    public void saveForcedAlternativeRegions()
    {
        String var = forcedAlternativeRegions.stream()
            .map(r->conversionService.convert(r, Integer.class))
            .map(String::valueOf)
            .collect(Collectors.joining(","));
        varDAO.merge("region.alternative.forced", var);
    }

    public void setPartialUpdate(Region region, boolean partial)
    {
        partialUpdates.get(region).setValueAndSave(partial ? 1L : null);
        LOG.info("{} partial update: {}", region, partial);
    }

    private UpdateContext getLadderUpdateContext(Region region, UpdateContext def)
    {
        Instant instant = forcedUpdateInstants.get(region).getValue();
        return instant == null
            || System.currentTimeMillis() - instant.toEpochMilli() >= FORCED_LADDER_SCAN_FRAME.toMillis()
            ? new UpdateContext(null, null)
            : def;
    }

    /*
        Many things rely on league existence, but some leagues could be absent on the ladder for various reasons.
        Precreate leagues for such occasions
     */
    private void createLeagues(Season season)
    {
        for(QueueType queueType : QueueType.values())
        {
            for(TeamType teamType : TeamType.values())
            {
                for(BaseLeague.LeagueType leagueType : BaseLeague.LeagueType.values())
                {
                    if(!BlizzardSC2API.isValidCombination(leagueType, queueType, teamType)) continue;

                    leagueDao.merge(new League(null, season.getId(), leagueType, queueType, teamType));
                }
            }
        }
    }

}
