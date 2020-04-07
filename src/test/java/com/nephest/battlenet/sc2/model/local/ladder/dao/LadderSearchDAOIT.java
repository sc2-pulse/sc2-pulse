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
package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.config.convert.*;
import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.LeagueStatsDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.model.local.ladder.MergedLadderSearchStatsResult;
import com.nephest.battlenet.sc2.model.local.ladder.PagedSearchResult;
import com.nephest.battlenet.sc2.web.service.blizzard.BlizzardSC2API;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import javax.sql.DataSource;
import java.util.*;

import static com.nephest.battlenet.sc2.model.local.SeasonGenerator.DEFAULT_SEASON_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringJUnitConfig(LadderSearchDAOIT.LadderTestConfig.class)
public class LadderSearchDAOIT
{

    public static final int TEAMS_PER_LEAGUE = 10;
    public static final List<Region> REGIONS = Collections.unmodifiableList(List.of(Region.values()));
    public static final int TEAMS_TOTAL = REGIONS.size() * (BaseLeague.LeagueType.values()).length * TEAMS_PER_LEAGUE;
    public static final List<BaseLeague.LeagueType> SEARCH_LEAGUES = Collections.unmodifiableList(List.of
    (
        BaseLeague.LeagueType.BRONZE,
        BaseLeague.LeagueType.SILVER,
        BaseLeague.LeagueType.GOLD,
        BaseLeague.LeagueType.PLATINUM,
        BaseLeague.LeagueType.DIAMOND, //skip masters for tests
        BaseLeague.LeagueType.GRANDMASTER
    ));

    @Autowired
    private LadderSearchDAO search;

    @BeforeAll
    public static void beforeAll(@Autowired SeasonGenerator generator, @Autowired LeagueStatsDAO leagueStatsDAO)
    {
        generator.generateSeason
        (
            REGIONS,
            List.of(BaseLeague.LeagueType.values()),
            QueueType.LOTV_4V4,
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            TEAMS_PER_LEAGUE
        );
        leagueStatsDAO.calculateForSeason(DEFAULT_SEASON_ID);
    }

    //skip masters league for test
    @CsvSource({"1, 279", "2, 199", "3, 159", "4, 119", "5, 79", "6, 39"})
    @ParameterizedTest
    public void test4v4Ladder(int page, int teamId)
    {
        QueueType queueType = QueueType.LOTV_4V4;
        TeamType teamType = TeamType.ARRANGED;
        BaseLeagueTier.LeagueTierType tierType = BaseLeagueTier.LeagueTierType.FIRST;
        Set<BaseLeague.LeagueType> leaguesSet = EnumSet.copyOf(SEARCH_LEAGUES);
        int expectedTeamCount = REGIONS.size() * SEARCH_LEAGUES.size() * TEAMS_PER_LEAGUE;
        int leagueTeamCount = REGIONS.size() * TEAMS_PER_LEAGUE;

        search.setResultsPerPage(leagueTeamCount);
        PagedSearchResult<List<LadderTeam>> result = search.find
        (
            DEFAULT_SEASON_ID,
            EnumSet.copyOf(REGIONS),
            leaguesSet,
            queueType,
            teamType,
            page
        );

        //validate meta
        assertEquals(expectedTeamCount, result.getMeta().getTotalCount());
        assertEquals(leagueTeamCount, result.getMeta().getPerPage());
        assertEquals( (int) Math.ceil(expectedTeamCount / (double) leagueTeamCount), result.getMeta().getPageCount());
        assertEquals(page, result.getMeta().getPage());

        //validate teams
        assertEquals(leagueTeamCount, result.getResult().size());
        int pagedIx = (page - 1) * leagueTeamCount;
        for(int i = 0; i < result.getResult().size(); i++, pagedIx++, teamId--)
        {
            LadderTeam team = result.getResult().get(i);
            //DESC order
            Region expectedRegion = REGIONS.get((REGIONS.size() - 1 - i / TEAMS_PER_LEAGUE % TEAMS_PER_LEAGUE % REGIONS.size()));
            assertEquals(expectedRegion, team.getRegion());
            BaseLeague.LeagueType expectedLeagueType = SEARCH_LEAGUES.get(SEARCH_LEAGUES.size() - 1 - pagedIx / leagueTeamCount % leagueTeamCount % SEARCH_LEAGUES.size());
            assertEquals(expectedLeagueType, team.getLeague().getType());
            assertEquals(queueType, team.getLeague().getQueueType());
            assertEquals(teamType, team.getLeague().getTeamType());
            assertEquals(tierType, team.getLeagueTierType());
            assertEquals(teamId, team.getRating());
            assertEquals(teamId, team.getWins());
            assertEquals(teamId + 1, team.getLosses());
            //validate members
            //no reason to sort members in query, sorting manually for testing
            team.getMembers().sort(Comparator.comparing(LadderTeamMember::getBattlenetId));
            for(int mIx = 0; mIx < team.getMembers().size(); mIx++)
            {
                LadderTeamMember member = team.getMembers().get(mIx);
                int accId = Integer.parseInt(teamId + "" + mIx);
                assertEquals("battletag#" + accId, member.getAccount().getBattleTag());
                assertEquals("character#" + accId, member.getCharacter().getName());
                assertEquals(1, member.getTerranGamesPlayed());
                assertEquals(2, member.getProtossGamesPlayed());
                assertEquals(3, member.getZergGamesPlayed());
                assertEquals(4, member.getRandomGamesPlayed());
            }
        }
    }

    @Test
    public void test4v4ReversedOffset()
    {
        QueueType queueType = QueueType.LOTV_4V4;
        TeamType teamType = TeamType.ARRANGED;
        BaseLeagueTier.LeagueTierType tierType = BaseLeagueTier.LeagueTierType.FIRST;
        Set<BaseLeague.LeagueType> leaguesSet = EnumSet.copyOf(SEARCH_LEAGUES);
        int teamsPerPage = 45;
        int expectedTeamCount = REGIONS.size() * SEARCH_LEAGUES.size() * TEAMS_PER_LEAGUE;
        int leagueTeamCount = REGIONS.size() * TEAMS_PER_LEAGUE;
        int lastPage = 6;
        int leftovers = 15;
        int teamId = expectedTeamCount - (expectedTeamCount - leftovers) - 1;

        search.setResultsPerPage(teamsPerPage);
        PagedSearchResult<List<LadderTeam>> result = search.find
        (
            DEFAULT_SEASON_ID,
            Set.of(Region.values()),
            leaguesSet,
            queueType,
            teamType,
            lastPage
        );

        //validate meta
        assertEquals(expectedTeamCount, result.getMeta().getTotalCount());
        assertEquals(teamsPerPage, result.getMeta().getPerPage());
        assertEquals( (int) Math.ceil(expectedTeamCount / (double) teamsPerPage), result.getMeta().getPageCount());
        assertEquals(lastPage, result.getMeta().getPage());

        //only leftovers are returned
        assertEquals(leftovers, result.getResult().size());
        int pagedIx = (lastPage - 1) * teamsPerPage;
        for(int i = 0; i < result.getResult().size(); i++, teamId--)
        {
            LadderTeam team = result.getResult().get(i);
            int compensatedIx = leagueTeamCount - leftovers + i;
            //DESC order
            Region expectedRegion = REGIONS.get((REGIONS.size() - 1 - compensatedIx / TEAMS_PER_LEAGUE % TEAMS_PER_LEAGUE % REGIONS.size()));
            assertEquals(expectedRegion, team.getRegion());
            BaseLeague.LeagueType expectedLeagueType = SEARCH_LEAGUES.get(SEARCH_LEAGUES.size() - 1 - pagedIx / leagueTeamCount % leagueTeamCount % SEARCH_LEAGUES.size());
            assertEquals(expectedLeagueType, team.getLeague().getType());
            assertEquals(queueType, team.getLeague().getQueueType());
            assertEquals(teamType, team.getLeague().getTeamType());
            assertEquals(tierType, team.getLeagueTierType());
            assertEquals(teamId, team.getRating());
            assertEquals(teamId, team.getWins());
            assertEquals(teamId + 1, team.getLosses());
            //validate members
            //no reason to sort members in query, sorting manually for testing
            team.getMembers().sort(Comparator.comparing(LadderTeamMember::getBattlenetId));
            for(int mIx = 0; mIx < team.getMembers().size(); mIx++)
            {
                LadderTeamMember member = team.getMembers().get(mIx);
                int accId = Integer.parseInt(teamId + "" + mIx);
                assertEquals("battletag#" + accId, member.getAccount().getBattleTag());
                assertEquals("character#" + accId, member.getCharacter().getName());
                assertEquals(1, member.getTerranGamesPlayed());
                assertEquals(2, member.getProtossGamesPlayed());
                assertEquals(3, member.getZergGamesPlayed());
                assertEquals(4, member.getRandomGamesPlayed());
            }
        }
    }

    @Test
    public void test4v4LeagueStats()
    {
        QueueType queueType = QueueType.LOTV_4V4;
        TeamType teamType = TeamType.ARRANGED;
        BaseLeagueTier.LeagueTierType tierType = BaseLeagueTier.LeagueTierType.FIRST;
        Set<BaseLeague.LeagueType> leaguesSet = EnumSet.copyOf(SEARCH_LEAGUES);
        MergedLadderSearchStatsResult stats = search.findStats
        (
            DEFAULT_SEASON_ID,
            Set.of(Region.values()),
            leaguesSet,
            queueType,
            teamType
        );

        int teamCount = REGIONS.size() * SEARCH_LEAGUES.size() * TEAMS_PER_LEAGUE;
        int regionTeamCount = SEARCH_LEAGUES.size() * TEAMS_PER_LEAGUE;
        int regionPlayerCount = regionTeamCount * queueType.getTeamFormat().getMemberCount(teamType);
        int regionGamesPlayed = (regionTeamCount * 1 + regionTeamCount * 2 + regionTeamCount * 3 + regionTeamCount * 4)
            * queueType.getTeamFormat().getMemberCount(teamType);
        int leagueTeamCount = REGIONS.size() * TEAMS_PER_LEAGUE;
        int leaguePlayerCount = leagueTeamCount * queueType.getTeamFormat().getMemberCount(teamType);
        int leagueGamesPlayed = (leagueTeamCount * 1 + leagueTeamCount * 2 + leagueTeamCount * 3 + leagueTeamCount * 4)
            * queueType.getTeamFormat().getMemberCount(teamType);
        for(Region region : REGIONS)
        {
            assertEquals(regionTeamCount, stats.getRegionTeamCount().get(region));
            assertEquals(regionPlayerCount, stats.getRegionPlayerCount().get(region));
            assertEquals(regionGamesPlayed, stats.getRegionGamesPlayed().get(region));
        }
        for(BaseLeague.LeagueType league : SEARCH_LEAGUES)
        {
            assertEquals(leagueTeamCount, stats.getLeagueTeamCount().get(league));
            assertEquals(leaguePlayerCount, stats.getLeaguePlayerCount().get(league));
            assertEquals(leagueGamesPlayed, stats.getLeagueGamesPlayed().get(league));
        }
        for(Race race : Race.values())
        {
            assertEquals((race.ordinal() + 1) * teamCount * queueType.getTeamFormat().getMemberCount(teamType), stats.getRaceGamesPlayed().get(race));
        }

    }


    @Configuration
    @ComponentScan("com.nephest.battlenet.sc2")
    public static class LadderTestConfig
    {

        @Bean
        public DataSource sc2StatsDataSource()
        {
            return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.HSQL)
                .generateUniqueName(true)
                .addScript("schema-hsql.sql")
                .build();
        }

        @Bean
        public NamedParameterJdbcTemplate sc2StatsNamedTemplate(DataSource ds)
        {
            return new NamedParameterJdbcTemplate(ds);
        }

        @Bean
        public ConversionService sc2StatsConversionService()
        {
            DefaultFormattingConversionService service = new DefaultFormattingConversionService();
            service.addConverter(new IdentifiableToIntegerConverter());
            service.addConverter(new IntegerToQueueTypeConverter());
            service.addConverter(new IntegerToLeagueTierTypeConverter());
            service.addConverter(new IntegerToLeagueTypeConverter());
            service.addConverter(new IntegerToRegionConverter());
            service.addConverter(new IntegerToTeamTypeConverter());
            return service;
        }

        @Bean
        public BlizzardSC2API blizzardSC2API()
        {
            return new BlizzardSC2API("pass");
        }

    }

}
