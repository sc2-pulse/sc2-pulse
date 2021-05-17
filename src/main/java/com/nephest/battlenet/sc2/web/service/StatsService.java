// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.blizzard.*;
import com.nephest.battlenet.sc2.model.local.*;
import com.nephest.battlenet.sc2.model.local.dao.*;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuple5;
import reactor.util.function.Tuples;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class StatsService
{

    private static final Logger LOG = LoggerFactory.getLogger(StatsService.class);

    public static final Version VERSION = Version.LOTV;
    public static final int STALE_LADDER_TOLERANCE = 3;
    public static final int STALE_LADDER_DEPTH = 10;
    public static final int DEFAULT_PLAYER_CHARACTER_STATS_HOURS_DEPTH = 27;
    public static final int LADDER_BATCH_SIZE = 400;

    @Autowired
    private StatsService statsService;

    @Value("${com.nephest.battlenet.sc2.ladder.alternative.regions:#{''}}")
    private Set<Region> alternativeRegions;

    @Value("${com.nephest.battlenet.sc2.ladder.forceUpdate:#{'false'}}")
    private boolean forceUpdate;

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
    private TeamMemberDAO teamMemberDao;
    private QueueStatsDAO queueStatsDAO;
    private LeagueStatsDAO leagueStatsDao;
    private PlayerCharacterStatsDAO playerCharacterStatsDAO;
    private VarDAO varDAO;
    private Validator validator;
    private ConversionService conversionService;

    private final AtomicBoolean isUpdating = new AtomicBoolean(false);
    private final Map<Integer, Instant> lastLeagueUpdates = new HashMap<>();

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
        TeamMemberDAO teamMemberDao,
        QueueStatsDAO queueStatsDAO,
        LeagueStatsDAO leagueStatsDao,
        PlayerCharacterStatsDAO playerCharacterStatsDAO,
        VarDAO varDAO,
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
        this.teamMemberDao = teamMemberDao;
        this.queueStatsDAO = queueStatsDAO;
        this.leagueStatsDao = leagueStatsDao;
        this.playerCharacterStatsDAO = playerCharacterStatsDAO;
        this.varDAO = varDAO;
        this.conversionService = conversionService;
        this.validator = validator;
    }

    @PostConstruct
    public void init()
    {
        //catch exceptions to allow service autowiring for tests
        try {
            loadLastUpdates();
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

    protected void setIsUpdating(boolean isUpdating)
    {
        this.isUpdating.set(isUpdating);
    }

    public boolean isUpdating()
    {
        return isUpdating.get();
    }

    public Set<Region> getAlternativeRegions()
    {
        return alternativeRegions;
    }

    public Map<Integer, Instant> getLastLeagueUpdates()
    {
        return lastLeagueUpdates;
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
    public boolean updateAll(Region[] regions, QueueType[] queues, BaseLeague.LeagueType[] leagues)
    {
        if(!isUpdating.compareAndSet(false, true))
        {
            LOG.info("Service is already updating");
            return false;
        }

        try
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

            isUpdating.set(false);
            long seconds = (System.currentTimeMillis() - start) / 1000;
            LOG.info("Updated all after {} seconds", seconds);
        }
        catch(RuntimeException ex)
        {
            isUpdating.set(false);
            throw ex;
        }

        return true;
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
    public boolean updateCurrent
    (Region[] regions, QueueType[] queues, BaseLeague.LeagueType[] leagues, boolean allStats)
    {
        if(!isUpdating.compareAndSet(false, true))
        {
            LOG.info("Service is already updating");
            return false;
        }

        try
        {
            Instant startInstant = Instant.now();
            long start = System.currentTimeMillis();

            checkStaleData();
            updateCurrentSeason(regions, queues, leagues, allStats);

            lastLeagueUpdates.put(queues.length, startInstant);
            saveLastUpdates();
            isUpdating.set(false);
            long seconds = (System.currentTimeMillis() - start) / 1000;
            LOG.info("Updated current after {} seconds", seconds);
        }
        catch(RuntimeException ex)
        {
            isUpdating.set(false);
            throw ex;
        }

        return true;
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
        updateSeasonStats(seasonId, regions, queues, leagues, true);
    }

    private void updateSeasonStats
    (int seasonId, Region[] regions, QueueType[] queues, BaseLeague.LeagueType[] leagues, boolean allStats)
    {
        if(allStats) queueStatsDAO.mergeCalculateForSeason(seasonId);
        leagueStatsDao.mergeCalculateForSeason(seasonId);
        teamDao.updateRanks(seasonId, regions, queues, TeamType.values(), leagues);
    }

    private void updateSeason(Region region, int seasonId, QueueType[] queues, BaseLeague.LeagueType[] leagues)
    {
        BlizzardSeason bSeason = api.getSeason(region, seasonId).block();
        Season season = seasonDao.merge(Season.of(bSeason, region));
        updateLeagues(bSeason, season, queues, leagues, false);
        LOG.debug("Updated leagues: {} {}", seasonId, region);
    }

    private void updateCurrentSeason
    (Region[] regions, QueueType[] queues, BaseLeague.LeagueType[] leagues, boolean allStats)
    {
        Integer seasonId = null;
        int maxSeason = seasonDao.getMaxBattlenetId();
        for(Region region : regions)
        {
            BlizzardSeason bSeason = api.getCurrentOrLastSeason(region, maxSeason).block();
            Season season = seasonDao.merge(Season.of(bSeason, region));
            updateOrAlternativeUpdate(bSeason, season, queues, leagues, true);
            seasonId = season.getBattlenetId();
            LOG.debug("Updated leagues: {} {}", seasonId, region);
        }
        teamStateDAO.removeExpired();
        if(seasonId != null)
        {
            if(queues.length == QueueType.getTypes(VERSION).size())
            {
                updateSeasonStats(seasonId, regions, queues, leagues, allStats);
                playerCharacterStatsDAO.mergeCalculate(
                    lastLeagueUpdates.get(queues.length) != null
                    ? OffsetDateTime.ofInstant(lastLeagueUpdates.get(queues.length), ZoneId.systemDefault())
                    : OffsetDateTime.now().minusHours(DEFAULT_PLAYER_CHARACTER_STATS_HOURS_DEPTH));
            }
            else
            {
                leagueStatsDao.mergeCalculateForSeason(seasonId);
                teamDao.updateRanks(seasonId, regions, queues, TeamType.values(), leagues);
            }
        }
    }

    private void updateOrAlternativeUpdate
    (BlizzardSeason bSeason, Season season, QueueType[] queues, BaseLeague.LeagueType[] leagues, boolean currentSeason)
    {
        if(!isAlternativeUpdate(season.getRegion(), currentSeason))
        {
            updateLeagues(bSeason, season, queues, leagues, currentSeason);
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
        boolean currentSeason
    )
    {
        Instant lastUpdated = currentSeason ? lastLeagueUpdates.get(queues.length) : null;
        LOG.debug("Updating season {} using {} checkpoint", season, lastUpdated);
        List<Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>> ladderIds =
            getLadderIds(getLeagueIds(bSeason, season.getRegion(), queues, leagues), currentSeason);
        int batches = (int) Math.ceil(ladderIds.size() / (double) LADDER_BATCH_SIZE);
        for(int i = 0; i < batches; i++)
        {
            int to = (i + 1) * LADDER_BATCH_SIZE;
            if (to > ladderIds.size()) to = ladderIds.size();
            List<Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>>  batch =
                ladderIds.subList(i * LADDER_BATCH_SIZE, to);
            statsService.updateLadders(season, batch, lastUpdated);
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
        api.getLadders(ladderIds)
            .doOnNext(l->{
                League league = leagueDao.merge(League.of(season, l.getT2().getT1()));
                LeagueTier tier = leagueTierDao.merge(LeagueTier.of(league, l.getT2().getT3()));
                Division division = saveDivision(season, league, tier, l.getT2().getT4());
                updateTeams(l.getT1().getTeams(), season, league, tier, division, lastUpdated);
                LOG.debug("Ladder saved: {} {} {}", season, division.getBattlenetId(), league);
            })
            .sequential()
            .blockLast();
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
                extractTeamMembers(bTeam.getMembers(), members, season, team);
                if(season.getBattlenetId().equals(curSeason)) states.add(TeamState.of(team));
            }
        }
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

    public void checkStaleData()
    {
        int maxSeason = seasonDao.getMaxBattlenetId();
        for(Region region : Region.values())
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

    private void loadLastUpdates()
    {
        String updatesVar = varDAO.find("ladder.updated").orElse(null);
        if(updatesVar == null || updatesVar.isEmpty()) {
            lastLeagueUpdates.clear();
            return;
        }

        for(String entry : updatesVar.split(",")) {
            String[] vals = entry.split("=");
            lastLeagueUpdates.put(Integer.valueOf(vals[0]), Instant.ofEpochMilli(Long.parseLong(vals[1])));
        }
        for(Map.Entry<Integer, Instant> entry : lastLeagueUpdates.entrySet())
            LOG.debug("Loaded lastLeagueUpdates entry: {}={}", entry.getKey(), entry.getValue());
    }

    private void saveLastUpdates()
    {
        StringBuilder sb = new StringBuilder();
        for(Map.Entry<Integer, Instant> entry : lastLeagueUpdates.entrySet()) {
            if(sb.length() > 0) sb.append(",");
            sb.append(entry.getKey()).append("=").append(entry.getValue().toEpochMilli());
        }
        varDAO.merge("ladder.updated", sb.toString());
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
