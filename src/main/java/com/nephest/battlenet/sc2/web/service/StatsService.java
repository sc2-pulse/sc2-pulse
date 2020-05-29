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
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import com.nephest.battlenet.sc2.web.service.blizzard.BlizzardSC2API;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.nephest.battlenet.sc2.model.BaseLeague.LeagueType.GRANDMASTER;
import static com.nephest.battlenet.sc2.model.TeamFormat.ARCHON;
import static com.nephest.battlenet.sc2.model.TeamFormat._1V1;

@Service
public class StatsService
{

    private static final Logger LOG = Logger.getLogger(StatsService.class.getName());

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
    private LeagueStatsDAO leagueStatsDao;
    private PlayerCharacterStatsDAO playerCharacterStatsDAO;
    private PostgreSQLUtils postgreSQLUtils;

    private List<Long> seasonsToUpdate = new ArrayList(BlizzardSC2API.MMR_SEASONS.keySet());
    private AtomicBoolean isUpdating = new AtomicBoolean(false);

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
        LeagueStatsDAO leagueStatsDao,
        PlayerCharacterStatsDAO playerCharacterStatsDAO,
        PostgreSQLUtils postgreSQLUtils
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
        this.leagueStatsDao = leagueStatsDao;
        this.playerCharacterStatsDAO = playerCharacterStatsDAO;
        this.postgreSQLUtils = postgreSQLUtils;
    }

    protected void setNestedService(StatsService statsService)
    {
        this.statsService = statsService;
    }

    protected void setIsUpdating(boolean isUpdating)
    {
        this.isUpdating.set(isUpdating);
    }

    @CacheEvict
    (
        cacheNames=
        {
            "search-seasons", "search-season-last",
            "search-ladder", "search-ladder-stats", "search-team-count",
            "search-ladder-league-bounds", "search-ladder-season"
        },
        allEntries=true
    )
    public boolean updateAll()
    {
        return updateAll(true);
    }

    @CacheEvict
    (
        cacheNames=
        {
            "search-seasons", "search-season-last",
            "search-ladder", "search-ladder-stats", "search-team-count",
            "search-ladder-league-bounds", "search-ladder-season"
        },
        allEntries=true
    )
    public boolean updateAll(boolean purgeStatus)
    {
        if(!isUpdating.compareAndSet(false, true))
        {
            LOG.info("Service is already updating");
            return false;
        }

        long start = System.currentTimeMillis();

        if(purgeStatus)
        {
            seasonsToUpdate = new ArrayList(BlizzardSC2API.MMR_SEASONS.keySet());
            Collections.sort(seasonsToUpdate);
        }

        for(Iterator<Long> iter = seasonsToUpdate.iterator(); iter.hasNext();)
        {
            Long sId = iter.next();
            updateSeason(sId);
            iter.remove();
            LOG.log(Level.INFO, "Updated season {0}", new Object[]{sId});
        }
        playerCharacterStatsDAO.mergeCalculateGlobal();
        postgreSQLUtils.analyze();

        isUpdating.set(false);
        long seconds = (System.currentTimeMillis() - start) / 1000;
        LOG.log(Level.INFO, "Updated all after {0} seconds", new Object[]{seconds});
        return true;
    }

    @CacheEvict
    (
        cacheNames=
            {
                "search-seasons", "search-season-last",
                "search-ladder", "search-ladder-stats", "search-team-count",
                "search-ladder-league-bounds", "search-ladder-season"
            },
        allEntries=true
    )
    public boolean updateMissing()
    {
        if(!isUpdating.compareAndSet(false, true))
        {
            LOG.info("Service is already updating");
            return false;
        }

        Long lastSeason = seasonDao.getMaxBattlenetId();
        final Long lastSeasonFinal = lastSeason == null ? 0 : lastSeason;
        List<Long> seasons = BlizzardSC2API.MMR_SEASONS.keySet().stream()
            .filter((id)->id > lastSeasonFinal)
            .sorted()
            .collect(Collectors.toList());

        long start = System.currentTimeMillis();

        for(Long season : seasons)
        {
            updateSeason(season);
            LOG.log(Level.INFO, "Updated season {0}", new Object[]{season});
        }
        playerCharacterStatsDAO.mergeCalculateGlobal();
        postgreSQLUtils.analyze();

        isUpdating.set(false);
        long seconds = (System.currentTimeMillis() - start) / 1000;
        LOG.log(Level.INFO, "Updated missing after {0} seconds", new Object[]{seconds});
        return true;
    }

    @CacheEvict
    (
        cacheNames=
        {
            "search-seasons", "search-season-last",
            "search-ladder", "search-ladder-stats", "search-team-count",
            "search-ladder-league-bounds", "search-ladder-season"
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

        long start = System.currentTimeMillis();

        updateCurrentSeason();
        postgreSQLUtils.analyze();

        isUpdating.set(false);
        long seconds = (System.currentTimeMillis() - start) / 1000;
        LOG.log(Level.INFO, "Updated current after {0} seconds", new Object[]{seconds});
        return true;
    }

    private void updateSeason(long seasonId)
    {
        for(Region region : Region.values())
        {
            updateSeason(region, seasonId);
        }
        leagueStatsDao.mergeCalculateForSeason(seasonId);
        playerCharacterStatsDAO.mergeCalculate(seasonId);
    }

    private void updateSeason(Region region, long seasonId)
    {
        BlizzardSeason bSeason = api.getSeason(seasonId);
        Season season = seasonDao.merge(Season.of(bSeason, region));
        updateLeagues(bSeason, season);
    }

    private void updateCurrentSeason()
    {
        Long seasonId = null;
        for(Region region : Region.values())
        {
            BlizzardSeason bSeason = api.getCurrentSeason(region).block();
            Season season = seasonDao.merge(Season.of(bSeason, region));
            updateLeagues(bSeason, season);
            seasonId = season.getBattlenetId();
        }
        if(seasonId != null)
        {
            leagueStatsDao.mergeCalculateForSeason(seasonId);
            playerCharacterStatsDAO.mergeCalculate(seasonId);
            playerCharacterStatsDAO.mergeCalculateGlobal();
        }
    }

    private void updateLeagues(BlizzardSeason bSeason, Season season)
    {
        for (League.LeagueType leagueType : League.LeagueType.values())
        {
            for (QueueType queueType : QueueType.getTypes(VERSION))
            {
                for (TeamType teamType : TeamType.values())
                {
                    if (!isValidCombination(leagueType, queueType, teamType)) continue;

                    BlizzardLeague bLeague = api.getLeague
                    (
                        season.getRegion(),
                        bSeason,
                        leagueType, queueType, teamType
                    ).block();
                    League league = League.of(season, bLeague);

                    leagueDao.merge(league);
                    updateLeagueTiers(bLeague, season, league);
                }
            }
        }
    }

    private boolean isValidCombination(League.LeagueType leagueType, QueueType queueType, TeamType teamType)
    {
        if
        (
            teamType == TeamType.RANDOM
            && (queueType.getTeamFormat() == ARCHON || queueType.getTeamFormat() == _1V1)
        ) return false;

        if
        (
            leagueType == GRANDMASTER
            && queueType.getTeamFormat() != ARCHON
            && queueType.getTeamFormat() != _1V1
        ) return false;

        return true;
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
        to = to <= bTier.getDivisions().length ? to : bTier.getDivisions().length;
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
                LOG.log(Level.SEVERE, ex.getMessage(), ex);
                LOG.info("Retrying transaction");
                statsService.updateDivisions(bTier.getDivisions(), season, league, tier, from, to);
            }
            from += perTransaction;
            to += perTransaction;
            to = to <= bTier.getDivisions().length ? to : bTier.getDivisions().length;
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
                    LOG.log
                    (
                        Level.INFO,
                        "Skipped invalid division {0}",
                        new Object[]{division.getBattlenetId()}
                    );
                }
                else
                {
                    throw ex;
                }
            }
        }
    }

    private void updateTeams
    (
        BlizzardTeam[] bTeams,
        Season season,
        League league,
        LeagueTier tier,
        Division division
    )
    {
        for (BlizzardTeam bTeam : bTeams)
        {
            Team team = Team.of(season, league, tier, division, bTeam);
            teamDao.merge(team);
            updateTeamMembers(bTeam.getMembers(), season, team);
        }
    }

    private void updateTeamMembers(BlizzardTeamMember[] bMembers, Season season, Team team)
    {
        for (BlizzardTeamMember bMember : bMembers)
        {
            //blizzard can send invalid member without account sometimes. Ignoring these entries
            if (bMember.getAccount() == null) continue;

            Account account = Account.of(bMember.getAccount());
            accountDao.merge(account);

            PlayerCharacter character = PlayerCharacter.of(account, season.getRegion(), bMember.getCharacter());
            playerCharacterDao.merge(character);

            TeamMember member = TeamMember.of(team, character, bMember.getRaces());
            teamMemberDao.merge(member);
        }
    }

}
