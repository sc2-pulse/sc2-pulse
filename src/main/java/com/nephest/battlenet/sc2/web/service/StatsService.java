// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.blizzard.*;
import com.nephest.battlenet.sc2.model.local.*;
import com.nephest.battlenet.sc2.model.local.dao.*;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.*;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class StatsService
{

    private static final Logger LOG = LoggerFactory.getLogger(StatsService.class);

    public static final Version VERSION = Version.LOTV;
    public static final int STALE_LADDER_TOLERANCE = 3;
    public static final int STALE_LADDER_DEPTH = 10;
    public static final int DEFAULT_PLAYER_CHARACTER_STATS_HOURS_DEPTH = 2;
    public static final int LADDER_BATCH_SIZE = 400;
    public static final int EXISTING_SEASON_DAYS_BEFORE_END_THRESHOLD = 10;

    @Autowired
    private StatsService statsService;

    @Value("${com.nephest.battlenet.sc2.ladder.alternative.regions:#{''}}")
    private Set<Region> alternativeRegions;

    @Value("${com.nephest.battlenet.sc2.ladder.forceUpdate:#{'false'}}")
    private boolean forceUpdate;

    private final Set<Integer> pendingStatsUpdates = new HashSet<>();
    private final Map<Region, Set<Long>> failedLadders = new EnumMap<>(Region.class);

    private AlternativeLadderService alternativeLadderService;
    private BlizzardSC2API api;
    private SeasonDAO seasonDao;
    private LeagueDAO leagueDao;
    private LeagueTierDAO leagueTierDao;
    private DivisionDAO divisionDao;
    private TeamDAO teamDao;
    private TeamStateDAO teamStateDAO;
    private AccountDAO accountDao;
    private PlayerCharacterDAO playerCharacterDao;
    private ClanDAO clanDAO;
    private TeamMemberDAO teamMemberDao;
    private QueueStatsDAO queueStatsDAO;
    private LeagueStatsDAO leagueStatsDao;
    private PlayerCharacterStatsDAO playerCharacterStatsDAO;
    private VarDAO varDAO;
    private PostgreSQLUtils postgreSQLUtils;
    private Validator validator;
    private ConversionService conversionService;

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
        TeamStateDAO teamStateDAO,
        AccountDAO accountDao,
        PlayerCharacterDAO playerCharacterDao,
        ClanDAO clanDAO,
        TeamMemberDAO teamMemberDao,
        QueueStatsDAO queueStatsDAO,
        LeagueStatsDAO leagueStatsDao,
        PlayerCharacterStatsDAO playerCharacterStatsDAO,
        VarDAO varDAO,
        PostgreSQLUtils postgreSQLUtils,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService,
        Validator validator
    )
    {
        this.alternativeLadderService = alternativeLadderService;
        this.api = api;
        this.seasonDao = seasonDao;
        this.leagueDao = leagueDao;
        this.leagueTierDao = leagueTierDao;
        this.divisionDao = divisionDao;
        this.teamDao = teamDao;
        this.teamStateDAO = teamStateDAO;
        this.accountDao = accountDao;
        this.playerCharacterDao = playerCharacterDao;
        this.clanDAO = clanDAO;
        this.teamMemberDao = teamMemberDao;
        this.queueStatsDAO = queueStatsDAO;
        this.leagueStatsDao = leagueStatsDao;
        this.playerCharacterStatsDAO = playerCharacterStatsDAO;
        this.varDAO = varDAO;
        this.postgreSQLUtils = postgreSQLUtils;
        this.conversionService = conversionService;
        this.validator = validator;
        for(Region r : Region.values()) failedLadders.put(r, new HashSet<>());
    }

    @PostConstruct
    public void init()
    {
        //catch exceptions to allow service autowiring for tests
        try {
            loadAlternativeRegions();
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

    @CacheEvict
    (
        cacheNames=
        {
            "search-seasons", "search-season-last",
            "search-ladder", "search-ladder-stats", "search-ladder-stats-bundle",
            "search-ladder-league-bounds", "search-ladder-season",
            "search-ladder-stats-queue",
            "ladder-skeleton"
        },
        allEntries=true
    )
    public void updateAll(Region[] regions, QueueType[] queues, BaseLeague.LeagueType[] leagues)
    {
        long start = System.currentTimeMillis();
        int lastSeasonIx = api.getLastSeason(Region.EU, seasonDao.getMaxBattlenetId()).block().getId() + 1;
        for(int season = BlizzardSC2API.FIRST_SEASON; season < lastSeasonIx; season++)
        {
            updateSeason(season, regions, queues, leagues);
            LOG.info("Updated season {}", season);
        }
        teamStateDAO.removeExpired();
        playerCharacterStatsDAO.mergeCalculate();

        long seconds = (System.currentTimeMillis() - start) / 1000;
        LOG.info("Updated all after {} seconds", seconds);
    }

    @CacheEvict
    (
        cacheNames=
        {
            "search-seasons", "search-season-last",
            "search-ladder", "search-ladder-stats", "search-ladder-stats-bundle",
            "search-ladder-league-bounds", "search-ladder-season",
            "search-ladder-stats-queue",
            "ladder-skeleton"
        },
        allEntries=true
    )
    public void updateCurrent
    (Region[] regions, QueueType[] queues, BaseLeague.LeagueType[] leagues, boolean allStats, UpdateContext updateContext)
    {
        long start = System.currentTimeMillis();

        checkStaleData(regions);
        updateCurrentSeason(regions, queues, leagues, allStats, updateContext);

        long seconds = (System.currentTimeMillis() - start) / 1000;
        LOG.info("Updated current for {} after {} seconds", regions, seconds);
    }

    public void updateLadders(int seasonId, Region region, Long[] ids)
    {
        Season season = seasonDao.merge(Season.of(api.getSeason(region, seasonId).block(), region));
        api.getLadders(region, ids)
            .doOnNext(l->statsService.saveLadder(season, l.getT1(), l.getT2(), alternativeLadderService))
        .sequential()
        .blockLast();

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

    private void updateSeason(int seasonId, Region[] regions, QueueType[] queues, BaseLeague.LeagueType[] leagues)
    {
        for(Region region : regions)
        {
            updateSeason(region, seasonId, queues, leagues);
        }
        postgreSQLUtils.vacuumAnalyze();
        updateSeasonStats(seasonId, true);
    }

    public void updatePendingStats(boolean allStats)
    {
        for(int season : pendingStatsUpdates) updateSeasonStats(season, allStats);
        pendingStatsUpdates.clear();
    }

    public void afterCurrentSeasonUpdate(UpdateContext updateContext, boolean allStats)
    {
        postgreSQLUtils.vacuumAnalyze();
        teamStateDAO.removeExpired();
        postgreSQLUtils.vacuumAnalyze();
        playerCharacterStatsDAO.mergeCalculate
        (
            updateContext.getInternalUpdate() != null
                ? OffsetDateTime.ofInstant(updateContext.getInternalUpdate(), ZoneId.systemDefault())
                : OffsetDateTime.now().minusHours(DEFAULT_PLAYER_CHARACTER_STATS_HOURS_DEPTH)
        );
        Set<Integer> pendingSeasons = Set.copyOf(pendingStatsUpdates);
        updatePendingStats(allStats);
        teamStateDAO.updateRanks(OffsetDateTime.ofInstant(updateContext.getInternalUpdate(), ZoneId.systemDefault()), pendingSeasons);
    }

    private void updateSeasonStats
    (int seasonId, boolean allStats)
    {
        if(allStats) queueStatsDAO.mergeCalculateForSeason(seasonId);
        leagueStatsDao.mergeCalculateForSeason(seasonId);
        teamDao.updateRanks(seasonId);
    }

    private void updateSeason(Region region, int seasonId, QueueType[] queues, BaseLeague.LeagueType[] leagues)
    {
        BlizzardSeason bSeason = api.getSeason(region, seasonId).block();
        Season season = seasonDao.merge(Season.of(bSeason, region));
        updateLeagues(bSeason, season, queues, leagues, false, new UpdateContext());
        LOG.debug("Updated leagues: {} {}", seasonId, region);
    }

    private void updateCurrentSeason
    (Region[] regions, QueueType[] queues, BaseLeague.LeagueType[] leagues, boolean allStats, UpdateContext updateContext)
    {
        //there can be two seasons here when a new season starts
        Set<Integer> seasons = new HashSet<>(2);
        int maxSeason = seasonDao.getMaxBattlenetId();
        for(Region region : regions)
        {
            BlizzardSeason bSeason = api.getCurrentOrLastSeason(region, maxSeason).block();
            Season season = seasonDao.merge(Season.of(bSeason, region));
            updateOrAlternativeUpdate(bSeason, season, queues, leagues, true, updateContext);
            seasons.add(season.getBattlenetId());
            LOG.debug("Updated leagues: {} {}", season.getBattlenetId(), region);
        }
        pendingStatsUpdates.addAll(seasons);
    }

    private void updateOrAlternativeUpdate
    (
        BlizzardSeason bSeason, Season season, QueueType[] queues, BaseLeague.LeagueType[] leagues,
        boolean currentSeason, UpdateContext updateContext
    )
    {
        if(!isAlternativeUpdate(season.getRegion(), currentSeason))
        {
            updateLeagues(bSeason, season, queues, leagues, currentSeason, updateContext);
        }
        else
        {
            if(queues.length < QueueType.getTypes(VERSION).size())
            {
                alternativeLadderService.updateThenSmartDiscoverSeason(season, queues, leagues);
            }
            else
            {
                alternativeLadderService.discoverSeason(season);
            }
        }
    }

    private boolean isAlternativeUpdate(Region region, boolean currentSeason)
    {
        return alternativeRegions.contains(region) && currentSeason;
    }

    private void updateLeagues
    (
        BlizzardSeason bSeason,
        Season season,
        QueueType[] queues,
        BaseLeague.LeagueType[] leagues,
        boolean currentSeason,
        UpdateContext updateContext
    )
    {
        LOG.debug("Updating season {} using {} checkpoint", season, updateContext.getExternalUpdate());
        List<Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>> ladderIds =
            getLadderIds(getLeagueIds(bSeason, season.getRegion(), queues, leagues), currentSeason);
        int batches = (int) Math.ceil(ladderIds.size() / (double) LADDER_BATCH_SIZE);
        for(int i = 0; i < batches; i++)
        {
            int to = (i + 1) * LADDER_BATCH_SIZE;
            if (to > ladderIds.size()) to = ladderIds.size();
            List<Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>>  batch =
                ladderIds.subList(i * LADDER_BATCH_SIZE, to);
            statsService.updateLadders(season, batch, updateContext.getExternalUpdate());
        }
    }

    @Transactional
    (
        //isolation = Isolation.READ_COMMITTED,
        propagation = Propagation.REQUIRES_NEW
    )
    public void updateLadders
    (Season season, List<Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>> ladderIds, Instant lastUpdated)
    {
        api.getLadders(ladderIds, failedLadders)
            .sequential()
            .toStream(BlizzardSC2API.SAFE_REQUESTS_PER_SECOND_CAP * 2)
            .forEach(l->
            {
                League league = leagueDao.merge(League.of(season, l.getT2().getT1()));
                LeagueTier tier = leagueTierDao.merge(LeagueTier.of(league, l.getT2().getT3()));
                Division division = saveDivision(season, league, tier, l.getT2().getT4());
                //force update previously failed ladders, this will pick up all skipped teams
                Instant lastUpdatedToUse = failedLadders.get(l.getT2().getT2()).remove(l.getT2().getT4().getLadderId())
                    ? null
                    : lastUpdated;
                updateTeams(l.getT1().getTeams(), season, league, tier, division, lastUpdatedToUse);
                LOG.debug
                (
                    "Ladder saved: {} {} {} {}",
                    season, division.getBattlenetId(), league, lastUpdatedToUse == null ? "forced" : ""
                );
            });
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
        Set<TeamState> states = new HashSet<>(bTeams.length, 1f);
        List<Tuple2<PlayerCharacter, Clan>> clans = new ArrayList<>();
        Integer curSeason = seasonDao.getMaxBattlenetId() == null ? 0 : seasonDao.getMaxBattlenetId();
        for (BlizzardTeam bTeam : bTeams)
        {
            Errors errors = new BeanPropertyBindingResult(bTeam, bTeam.toString());
            validator.validate(bTeam, errors);
            if(!errors.hasErrors() && isValidTeam(bTeam, memberCount))
            {
                Team team = saveTeam(season, league, tier, division, bTeam);
                //old team, nothing to update
                if(team == null) continue;
                extractTeamMembers(bTeam.getMembers(), members, clans, season, team);
                if(season.getBattlenetId().equals(curSeason)) states.add(TeamState.of(team));
            }
        }
        saveClans(clanDAO, clans);
        saveMembersConcurrently(members);
        if(states.size() > 0) teamStateDAO.saveState(states.toArray(TeamState[]::new));
    }

    private Team saveTeam
    (
        Season season,
        League league,
        LeagueTier tier,
        Division division,
        BlizzardTeam bTeam
    )
    {
        return teamDao.merge(Team.of(season, league, tier, division, bTeam, teamDao));
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
        List<Tuple2<PlayerCharacter, Clan>> clans,
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
            if(bMember.getClan() != null)
                clans.add(Tuples.of(character, Clan.of(bMember.getClan(), character.getRegion())));
            members.add(Tuples.of(account, character, member));
        }
    }

    //this ensures the consistent order for concurrent entities(accounts and players)
    private void saveMembersConcurrently(List<Tuple3<Account, PlayerCharacter, TeamMember>> members)
    {
        if(members.size() == 0) return;

        Set<TeamMember> teamMembers = new HashSet<>(members.size(), 1.0F);

        members.sort(Comparator.comparing(a -> a.getT1().getBattleTag()));
        for(Tuple3<Account, PlayerCharacter, TeamMember> curMembers : members) accountDao.merge(curMembers.getT1());

        members.sort(Comparator.comparing(a -> a.getT2().getBattlenetId()));
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

    public static void saveClans
    (ClanDAO clanDAO, List<Tuple2<PlayerCharacter, Clan>> clans)
    {
        if(clans.isEmpty()) return;

        clanDAO.merge(clans.stream()
            .map(Tuple2::getT2)
            .toArray(Clan[]::new)
        );
        for(Tuple2<PlayerCharacter, Clan> t : clans) t.getT1().setClanId(t.getT2().getId());
    }

    public static List<Tuple5<Region, BlizzardSeason, BaseLeague.LeagueType, QueueType, TeamType>> getLeagueIds
    (
        BlizzardSeason bSeason,
        Region region,
        QueueType[] queues,
        BaseLeague.LeagueType[] leagues
    )
    {
        List<Tuple5<Region, BlizzardSeason, BaseLeague.LeagueType, QueueType, TeamType>> leagueIds = new ArrayList<>();
        for(BaseLeague.LeagueType league : leagues)
        {
            for(QueueType queue : queues)
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

    private List<Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>> getLadderIds
    (Iterable<? extends Tuple5<Region, BlizzardSeason, BaseLeague.LeagueType, QueueType, TeamType>> ids, boolean cur)
    {
        List<Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>> ladderIds = new ArrayList<>();
        api.getLeagues(ids, cur)
            .flatMap(l-> Flux.fromStream(
                Arrays.stream(l.getT1().getTiers())
                    .flatMap(t-> Arrays.stream(t.getDivisions())
                        .map(d->Tuples.of(l.getT1(), l.getT2(), t, d)))))
            .sequential()
            .doOnNext(ladderIds::add)
            .blockLast();
        return ladderIds;
    }

    public long getMaxLadderId(BlizzardSeason bSeason, Region region)
    {
        List<Tuple5<Region, BlizzardSeason, BaseLeague.LeagueType, QueueType, TeamType>> leagueIds =
            getLeagueIds(bSeason, region, QueueType.getTypes(VERSION).toArray(QueueType[]::new), BaseLeague.LeagueType.values());

        AtomicLong max = new AtomicLong(-1);
        api.getLeagues(leagueIds, true)
            .doOnNext(l->{
                long maxId = Arrays.stream(l.getT1().getTiers())
                    .flatMapToLong(t->Arrays.stream(t.getDivisions()).mapToLong(BlizzardTierDivision::getLadderId))
                    .max().orElse(-1L);
                max.getAndUpdate(c->Math.max(c, maxId));
            })
            .sequential()
            .blockLast();
        return max.get();
    }

    public BlizzardSeason getCurrentOrLastOrExistingSeason(Region region, int maxSeason)
    {
        try
        {
            return api.getCurrentOrLastSeason(region, maxSeason).block();
        }
        catch(RuntimeException ex)
        {
            if(!(ExceptionUtils.getRootCause(ex) instanceof WebClientResponseException)) throw ex;
            LOG.warn(ExceptionUtils.getRootCauseMessage(ex));
        }
        Season s = seasonDao.findListByRegion(region).stream()
            .filter(ss->ss.getBattlenetId().equals(maxSeason))
            .findAny().orElseThrow();
        if(!LocalDate.now().isBefore(s.getEnd().minusDays(EXISTING_SEASON_DAYS_BEFORE_END_THRESHOLD)))
            throw new IllegalStateException("Could not find any season for " + region.name());
        return new BlizzardSeason(s.getBattlenetId(), s.getYear(), s.getNumber(), s.getStart(), s.getEnd());
    }

    public void checkStaleData(Region[] regions)
    {
        int maxSeason = seasonDao.getMaxBattlenetId();
        for(Region region : regions)
        {
            BlizzardSeason bSeason = api.getCurrentOrLastSeason(region, maxSeason).block();
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

    private Mono<Tuple3<Region, BlizzardPlayerCharacter[], Long>> chainStaleDataCheck(Region region, long ladderId, int count)
    {
        return Mono.defer(()->
            api.getProfileLadderId(region, ladderId)
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

}
