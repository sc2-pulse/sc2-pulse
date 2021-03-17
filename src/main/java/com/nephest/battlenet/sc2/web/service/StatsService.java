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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuple5;
import reactor.util.function.Tuples;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class StatsService
{

    private static final Logger LOG = LoggerFactory.getLogger(StatsService.class);

    public static final Version VERSION = Version.LOTV;
    public static final int STALE_LADDER_TOLERANCE = 3;
    public static final int DEFAULT_PLAYER_CHARACTER_STATS_HOURS_DEPTH = 27;

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
    private Validator validator;
    private boolean ignoreAlternativeData;

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
        this.validator = validator;
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

    @Autowired
    protected void setIgnoreAlternativeData
    (@Value("${com.nephest.battlenet.sc2.ladder.ignoreAlternativeData:#{'false'}}") boolean ignoreAlternativeData)
    {
        this.ignoreAlternativeData = ignoreAlternativeData;
    }

    @CacheEvict
    (
        cacheNames=
        {
            "search-seasons", "search-season-last",
            "search-ladder", "search-ladder-stats", "search-ladder-stats-bundle",
            "search-ladder-league-bounds", "search-ladder-season",
            "search-ladder-stats-queue"
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
            "search-ladder-stats-queue"
        },
        allEntries=true
    )
    public boolean updateCurrent(Region[] regions, QueueType[] queues, BaseLeague.LeagueType[] leagues)
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
            updateCurrentSeason(regions, queues, leagues);

            lastLeagueUpdates.put(queues.length, startInstant);
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
        Division division = alternativeLadderService.getOrCreate1v1Division(
            season, lKey.getQueueId(), lKey.getTeamType(), lKey.getLeagueId(), id);
        updateTeams(bLadder.getTeams(), season, league, tier, division, null);
    }

    private void updateSeason(int seasonId, Region[] regions, QueueType[] queues, BaseLeague.LeagueType[] leagues)
    {
        for(Region region : regions)
        {
            updateSeason(region, seasonId, queues, leagues);
        }
        updateSeasonStats(seasonId, regions, queues, leagues);
    }

    private void updateSeasonStats(int seasonId, Region[] regions, QueueType[] queues, BaseLeague.LeagueType[] leagues)
    {
        queueStatsDAO.mergeCalculateForSeason(seasonId);
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

    private void updateCurrentSeason(Region[] regions, QueueType[] queues, BaseLeague.LeagueType[] leagues)
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
                updateSeasonStats(seasonId, regions, queues, leagues);
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
                alternativeLadderService.updateThenSmartDiscoverSeason(season, leagues);
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
        for (League.LeagueType leagueType : leagues)
        {
            for (QueueType queueType : queues)
            {
                for (TeamType teamType : TeamType.values())
                {
                    if (!BlizzardSC2API.isValidCombination(leagueType, queueType, teamType)) continue;

                    BlizzardLeague bLeague = api.getLeague
                    (
                        season.getRegion(),
                        bSeason,
                        leagueType, queueType, teamType,
                        currentSeason
                    ).block();
                    League league = League.of(season, bLeague);

                    leagueDao.merge(league);
                    updateLeagueTiers(
                        bLeague, season, league,
                        currentSeason ? lastLeagueUpdates.get(queues.length) : null);
                }
            }
        }
    }

    private void updateLeagueTiers(BlizzardLeague bLeague, Season season, League league, Instant lastUpdateStart)
    {
        for (BlizzardLeagueTier bTier : bLeague.getTiers())
        {
            LeagueTier tier = LeagueTier.of(league, bTier);
            leagueTierDao.merge(tier);
            updateDivisions(bTier.getDivisions(), season, league, tier, lastUpdateStart);
        }
    }

    private void updateDivisions
    (
        BlizzardTierDivision[] divisions,
        Season season,
        League league,
        LeagueTier tier,
        Instant lastUpdateStart
    )
    {
        api.getLadders(season.getRegion(), divisions)
            .doOnNext(t->statsService.saveLadder(season, league, tier, t, lastUpdateStart))
            .sequential()
            .blockLast();
    }

    @Transactional
    (
        //isolation = Isolation.READ_COMMITTED,
        propagation = Propagation.REQUIRES_NEW
    )
    public void saveLadder
    (
        Season season,
        League league,
        LeagueTier tier,
        Tuple2<BlizzardLadder, BlizzardTierDivision> t,
        Instant lastUpdateStart
    )
    {
        BlizzardTierDivision bDivision = t.getT2();
        Division division = saveDivision(season, league, tier, bDivision);
        updateTeams(t.getT1().getTeams(), season, league, tier, division, lastUpdateStart);
    }

    public Division saveDivision
    (
        Season season,
        League league,
        LeagueTier tier,
        BlizzardTierDivision bDivision
    )
    {
        //alternative ladder update updates only 1v1
        if(ignoreAlternativeData || league.getQueueType() != QueueType.LOTV_1V1)
            return divisionDao.merge(Division.of(tier, bDivision));

        /*
            Alternative ladder update doesn't have tier info, so it creates divisions with the default tier.
            Find such divisions and update their tier.
         */
        Division division = divisionDao.findDivision(
            season.getBattlenetId(), season.getRegion(), QueueType.LOTV_1V1, TeamType.ARRANGED, bDivision.getLadderId())
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
        if(lastUpdateStart != null) bTeams = Arrays.stream(bTeams)
            .filter(t->t.getLastPlayedTimeStamp().isAfter(lastUpdateStart)).toArray(BlizzardTeam[]::new);
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
        //alternative ladder update updates only 1v1
        if(ignoreAlternativeData || league.getQueueType() != QueueType.LOTV_1V1)
            return teamDao.merge(Team.of(season, tier, division, bTeam));

        //alternative ladder does not have battlenet id, find such teams and update them
        PlayerCharacter playerCharacter =
            playerCharacterDao.find(
                season.getRegion(),
                bTeam.getMembers()[0].getCharacter().getRealm(),
                bTeam.getMembers()[0].getCharacter().getId())
            .orElse((null));
        if(playerCharacter == null) return teamDao.merge(Team.of(season, tier, division,bTeam));

        BaseLocalTeamMember member = new BaseLocalTeamMember();
        for(BlizzardTeamMemberRace race : bTeam.getMembers()[0].getRaces())
            member.setGamesPlayed(race.getRace(), race.getGamesPlayed());
        Map.Entry<Team, List<TeamMember>> teamEntry = teamDao
            .find1v1TeamByFavoriteRace(season.getBattlenetId(), playerCharacter, member.getFavoriteRace()).orElse(null);
        if(teamEntry != null)
        {
            Team team = teamEntry.getKey();
            boolean wasAlternative = team.getBattlenetId() == null;

            team.setLeagueTierId(tier.getId());
            team.setDivisionId(division.getId());
            team.setBattlenetId(bTeam.getId());
            team.setRating(bTeam.getRating());
            team.setPoints(bTeam.getPoints());
            team.setWins(bTeam.getWins());
            team.setLosses(bTeam.getLosses());
            team.setTies(bTeam.getTies());
            return wasAlternative ? teamDao.mergeById(team, forceUpdate) : teamDao.merge(team);
        }

        return teamDao.merge(Team.of(season, tier, division,bTeam));
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

    public long getMaxLadderId(BlizzardSeason bSeason, Region region)
    {
        List<Tuple5<Region, BlizzardSeason, BaseLeague.LeagueType, QueueType, TeamType>> leagueIds = new ArrayList<>();
        for(BaseLeague.LeagueType league : BaseLeague.LeagueType.values())
        {
            for(QueueType queue : QueueType.getTypes(VERSION))
            {
                for(TeamType team : TeamType.values())
                {
                    if(!BlizzardSC2API.isValidCombination(league, queue, team)) continue;

                    leagueIds.add(Tuples.of(region, bSeason, league, queue, team));
                }
            }
        }

        AtomicLong max = new AtomicLong(-1);
        api.getLeagues(leagueIds, true)
            .doOnNext(l->{
                long maxId = Arrays.stream(l.getTiers())
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
            api.getProfileLadderId(region, maxId + STALE_LADDER_TOLERANCE)
                .doOnNext(l->{
                    if(alternativeRegions.add(region))
                        LOG.warn("Stale data detected for {}, added this region to alternative update", region);
                })
                .onErrorResume(t->{
                    if(alternativeRegions.remove(region))
                        LOG.info("{} now returns fresh data, removed it from alternative update", region);
                    return Mono.empty();
                })
                .block();
        }
    }

}
