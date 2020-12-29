// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.Version;
import com.nephest.battlenet.sc2.model.blizzard.*;
import com.nephest.battlenet.sc2.model.local.*;
import com.nephest.battlenet.sc2.model.local.dao.*;
import com.nephest.battlenet.sc2.web.service.blizzard.BlizzardSC2API;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class StatsService
{

    private static final Logger LOG = LoggerFactory.getLogger(StatsService.class);

    public static final Version VERSION = Version.LOTV;
    public static final int UPDATE_ALL_MAX_TRIES = 5;
    public static final int MEMBERS_PER_TRANSACTION = 400;

    @Autowired
    private StatsService statsService;

    private BlizzardSC2API api;
    private SeasonDAO seasonDao;
    private LeagueDAO leagueDao;
    private LeagueTierDAO leagueTierDao;
    private DivisionDAO divisionDao;
    private TeamDAO teamDao;
    private AccountDAO accountDao;
    private PlayerCharacterDAO playerCharacterDao;
    private TeamMemberDAO teamMemberDao;
    private QueueStatsDAO queueStatsDAO;
    private LeagueStatsDAO leagueStatsDao;
    private PlayerCharacterStatsDAO playerCharacterStatsDAO;
    private Validator validator;

    private final AtomicBoolean isUpdating = new AtomicBoolean(false);

    public StatsService(){}

    @Autowired
    public StatsService
    (
        BlizzardSC2API api,
        SeasonDAO seasonDao,
        LeagueDAO leagueDao,
        LeagueTierDAO leagueTierDao,
        DivisionDAO divisionDao,
        TeamDAO teamDao,
        AccountDAO accountDao,
        PlayerCharacterDAO playerCharacterDao,
        TeamMemberDAO teamMemberDao,
        QueueStatsDAO queueStatsDAO,
        LeagueStatsDAO leagueStatsDao,
        PlayerCharacterStatsDAO playerCharacterStatsDAO,
        Validator validator
    )
    {
        this.api = api;
        this.seasonDao = seasonDao;
        this.leagueDao = leagueDao;
        this.leagueTierDao = leagueTierDao;
        this.divisionDao = divisionDao;
        this.teamDao = teamDao;
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

    @CacheEvict
    (
        cacheNames=
        {
            "search-seasons", "search-season-last",
            "search-ladder", "search-ladder-stats", "search-ladder-stats-bundle", "search-team-count",
            "search-ladder-league-bounds", "search-ladder-season",
            "search-ladder-stats-queue"
        },
        allEntries=true
    )
    public boolean updateAll()
    {
        if(!isUpdating.compareAndSet(false, true))
        {
            LOG.info("Service is already updating");
            return false;
        }

        try
        {
            long start = System.currentTimeMillis();
            int lastSeasonIx = api.getLastSeason(Region.EU).getId() + 1;
            for(int season = BlizzardSC2API.FIRST_SEASON; season < lastSeasonIx; season++)
            {
                updateSeason(season);
                LOG.info("Updated season {}", season);
            }
            playerCharacterStatsDAO.mergeCalculateGlobal();

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
            "search-ladder", "search-ladder-stats", "search-ladder-stats-bundle", "search-team-count",
            "search-ladder-league-bounds", "search-ladder-season",
            "search-ladder-stats-queue"
        },
        allEntries=true
    )
    public boolean updateCurrent()
    {
        if(!isUpdating.compareAndSet(false, true))
        {
            LOG.info("Service is already updating");
            return false;
        }

        try
        {
            long start = System.currentTimeMillis();

            updateCurrentSeason();

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

    private void updateSeason(int seasonId)
    {
        for(Region region : Region.values())
        {
            updateSeason(region, seasonId);
        }
        updateSeasonStats(seasonId);
    }

    private void updateSeasonStats(int seasonId)
    {
        queueStatsDAO.mergeCalculateForSeason(seasonId);
        leagueStatsDao.mergeCalculateForSeason(seasonId);
        playerCharacterStatsDAO.mergeCalculate(seasonId);
        teamDao.updateRanks(seasonId);
    }

    private void updateSeason(Region region, int seasonId)
    {
        BlizzardSeason bSeason = api.getSeason(region, seasonId).block();
        Season season = seasonDao.merge(Season.of(bSeason, region));
        updateLeagues(bSeason, season, false);
        LOG.debug("Updated leagues: {} {}", seasonId, region);
    }

    private void updateCurrentSeason()
    {
        Integer seasonId = null;
        for(Region region : Region.values())
        {
            BlizzardSeason bSeason = api.getCurrentOrLastSeason(region).block();
            Season season = seasonDao.merge(Season.of(bSeason, region));
            updateLeagues(bSeason, season, true);
            seasonId = season.getBattlenetId();
            LOG.debug("Updated leagues: {} {}", seasonId, region);
        }
        if(seasonId != null)
        {
            updateSeasonStats(seasonId);
            playerCharacterStatsDAO.mergeCalculateGlobal();
        }
    }

    private void updateLeagues(BlizzardSeason bSeason, Season season, boolean currentSeason)
    {
        for (League.LeagueType leagueType : League.LeagueType.values())
        {
            for (QueueType queueType : QueueType.getTypes(VERSION))
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
                    updateLeagueTiers(bLeague, season, league);
                }
            }
        }
    }

    private void updateLeagueTiers(BlizzardLeague bLeague, Season season, League league)
    {
        for (BlizzardLeagueTier bTier : bLeague.getTiers())
        {
            LeagueTier tier = LeagueTier.of(league, bTier);
            leagueTierDao.merge(tier);
            updateDivisions(bTier, season, league, tier);
        }
    }


    protected void updateDivisions(BlizzardLeagueTier bTier, Season season, League league, LeagueTier tier)
    {
        int from = 0;
        int perTransaction =
            MEMBERS_PER_TRANSACTION /
            //members in one division
            (league.getQueueType().getTeamFormat().getMemberCount(league.getTeamType()) * 100);
        perTransaction = perTransaction == 0 ? 1 : perTransaction;
        int to = from + perTransaction;
        to = Math.min(to, bTier.getDivisions().length);
        while(from < bTier.getDivisions().length)
        {
            /*
                All retry settings are configured on lower level APIs
                We can encounter an exception here in a rare occasion
                like transaction timeout. Retry it once to prevent whole season retry
            */
            try
            {
                statsService.updateDivisions(bTier.getDivisions(), season, league, tier, from, to);
            }
            catch(Exception ex)
            {
                LOG.error(ex.getMessage(), ex);
                LOG.info("Retrying transaction");
                statsService.updateDivisions(bTier.getDivisions(), season, league, tier, from, to);
            }
            from += perTransaction;
            to += perTransaction;
            to = Math.min(to, bTier.getDivisions().length);
        }
    }

    @Transactional
    (
        //isolation = Isolation.READ_COMMITTED,
        propagation = Propagation.REQUIRES_NEW
    )
    public void updateDivisions
    (
        BlizzardTierDivision[] divisions,
        Season season,
        League league,
        LeagueTier tier,
        int from, int to
    )
    {
        for (int i = from; i < to; i++)
        {
            BlizzardTierDivision bDivision = divisions[i];
            Division division = Division.of(tier, bDivision);
            divisionDao.merge(division);
            try
            {
                updateTeams(api.getLadder(season.getRegion(), bDivision).block().getTeams(), season, league, tier, division);
                //A lot of garbage here, hint the GC
                System.gc();
            }
            catch(RuntimeException ex)
            {
                if(ex.getCause() != null && ex.getCause() instanceof WebClientResponseException)
                {
                    /*
                        api is retrying failed requests
                        if exception is thrown there is nothing we can do
                        skip failed division
                    */
                    LOG.info
                    (
                        "Skipped invalid division {}", division.getBattlenetId());
                }
                else
                {
                    throw ex;
                }
            }
        }
    }

    protected void updateTeams
    (
        BlizzardTeam[] bTeams,
        Season season,
        League league,
        LeagueTier tier,
        Division division
    )
    {
        int memberCount = league.getQueueType().getTeamFormat().getMemberCount(league.getTeamType());
        Set<TeamMember> members = new HashSet<>(bTeams.length * memberCount, 1f);
        for (BlizzardTeam bTeam : bTeams)
        {
            Errors errors = new BeanPropertyBindingResult(bTeam, bTeam.toString());
            validator.validate(bTeam, errors);
            if(!errors.hasErrors() && isValidTeam(bTeam, memberCount))
            {
                Team team = Team.of(season, league, tier, division, bTeam);
                teamDao.merge(team);
                extractTeamMembers(bTeam.getMembers(), members, season, team);
            }
        }
        if(members.size() > 0) teamMemberDao.merge(members.toArray(TeamMember[]::new));
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

    private void extractTeamMembers(BlizzardTeamMember[] bMembers, Set<TeamMember> dest, Season season, Team team)
    {
        for (BlizzardTeamMember bMember : bMembers)
        {
            //blizzard can send invalid member without account sometimes. Ignoring these entries
            if (bMember.getAccount() == null) continue;

            Account account = Account.of(bMember.getAccount(), season.getRegion());
            accountDao.merge(account);

            PlayerCharacter character = PlayerCharacter.of(account, season.getRegion(), bMember.getCharacter());
            playerCharacterDao.merge(character);

            TeamMember member = TeamMember.of(team, character, bMember.getRaces());
            dest.add(member);
        }
    }

}
