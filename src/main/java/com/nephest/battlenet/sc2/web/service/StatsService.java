/*-
 * =========================LICENSE_START=========================
 * SC2 Ladder Generator
 * %%
 * Copyright (C) 2020 Oleksandr Masniuk
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END=========================
 */
package com.nephest.battlenet.sc2.web.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.logging.*;

import static javax.transaction.Transactional.TxType.*;

import org.springframework.beans.factory.annotation.*;
import org.springframework.cache.annotation.*;
import org.springframework.web.reactive.function.client.*;
import org.springframework.transaction.annotation.*;

import com.nephest.battlenet.sc2.web.service.blizzard.BlizzardSC2API;
import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.blizzard.*;
import com.nephest.battlenet.sc2.model.local.*;
import com.nephest.battlenet.sc2.model.local.dao.*;
import static com.nephest.battlenet.sc2.model.TeamFormat.*;
import static com.nephest.battlenet.sc2.model.BaseLeague.LeagueType.*;

@Service
public class StatsService
{

    private static final Logger LOG = Logger.getLogger(StatsService.class.getName());

    public static final Version VERSION = Version.LOTV;
    public static final int UPDATE_ALL_MAX_TRIES = 5;
    //it is essential to have a minimum of 5000 here due to team index recalculation
    public static final int MEMBERS_PER_TRANSACTION = 10000;

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

    private List<Long> seasonsToUpdate = new ArrayList(BlizzardSC2API.MMR_SEASONS.keySet());

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
        LeagueStatsDAO leagueStatsDao
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
    }

    protected void setNestedService(StatsService statsService)
    {
        this.statsService = statsService;
    }

    public void updateAll()
    {
        updateAll(true);
    }

    @CacheEvict
    (
        cacheNames=
        {
            "search-seasons", "search-season-last",
            "search-ladder", "search-ladder-stats",
            "search-ladder-league-bounds", "search-ladder-season"
        },
        allEntries=true
    )
    public void updateAll(boolean purgeStatus)
    {
        long start = System.currentTimeMillis();

        if(purgeStatus)
        {
            seasonsToUpdate = new ArrayList(BlizzardSC2API.MMR_SEASONS.keySet());
            Collections.sort(seasonsToUpdate);
        }

        int tries = 0;
        while(tries < UPDATE_ALL_MAX_TRIES)
        {
            try
            {
                for(Iterator<Long> iter = seasonsToUpdate.iterator(); iter.hasNext();)
                {
                    Long sId = iter.next();
                    updateSeason(sId);
                    iter.remove();
                    LOG.log(Level.INFO, "Updated season {0}", new Object[]{sId});
                }
                tries = UPDATE_ALL_MAX_TRIES;
            }
            catch(RuntimeException ex)
            {
                LOG.log(Level.SEVERE, ex.getMessage(), ex);
                tries++;
                if(tries == UPDATE_ALL_MAX_TRIES) throw ex;
            }
        }

        long seconds = (System.currentTimeMillis() - start) / 1000;
        LOG.log(Level.INFO, "Updated all after {0} seconds", new Object[]{seconds});
    }

    @CacheEvict
    (
        cacheNames=
        {
            "search-seasons", "search-season-last",
            "search-ladder", "search-ladder-stats",
            "search-ladder-league-bounds", "search-ladder-season"
        },
        allEntries=true
    )
    public void updateCurrent()
    {
        long start = System.currentTimeMillis();

         //should be change to update current season when bnet api is up
        updateSeason(42);

        long seconds = (System.currentTimeMillis() - start) / 1000;
        LOG.log(Level.INFO, "Updated current after {0} seconds", new Object[]{seconds});
    }

    private void updateSeason(long seasonId)
    {
        for(Region region : Region.values())
        {
            updateSeason(region, seasonId);
        }
        leagueStatsDao.calculateForSeason(seasonId);
    }

    private void updateSeason(Region region, long seasonId)
    {
        BlizzardSeason bSeason = api.getSeason(seasonId);
        Season season = seasonDao.merge(Season.of(bSeason, region));
        updateLeagues(bSeason, season);
    }

    private void updateCurrentSeason(Region region)
    {
        BlizzardSeason bSeason = api.getCurrentSeason(region);
        Season season = seasonDao.merge(Season.of(bSeason, region));
        updateLeagues(bSeason, season);
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
                    );
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
                updateTeams(api.getTeams(season.getRegion(), bDivision), season, league, tier, division);
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

            Account account = Account.of(season.getRegion(), bMember.getAccount());
            accountDao.merge(account);

            PlayerCharacter character = PlayerCharacter.of(account, bMember.getCharacter());
            playerCharacterDao.merge(character);

            TeamMember member = TeamMember.of(team, character, bMember.getRaces());
            teamMemberDao.merge(member);
        }
    }

}
