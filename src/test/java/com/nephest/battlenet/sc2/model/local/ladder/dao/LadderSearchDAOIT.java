// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.LeagueStatsDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.model.local.ladder.MergedLadderSearchStatsResult;
import com.nephest.battlenet.sc2.model.local.ladder.PagedSearchResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

import static com.nephest.battlenet.sc2.model.local.SeasonGenerator.DEFAULT_SEASON_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;


@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
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
    public static final QueueType QUEUE_TYPE = QueueType.LOTV_4V4;
    public static final TeamType TEAM_TYPE = TeamType.ARRANGED;
    public static final BaseLeagueTier.LeagueTierType TIER_TYPE = BaseLeagueTier.LeagueTierType.FIRST;
    public static final Set<BaseLeague.LeagueType> LEAGUES_SET = Collections.unmodifiableSet(EnumSet.copyOf(SEARCH_LEAGUES));

    @Autowired
    private LadderSearchDAO search;

    @BeforeAll
    public static void beforeAll
    (
        @Autowired DataSource dataSource,
        @Autowired SeasonGenerator generator,
        @Autowired LeagueStatsDAO leagueStatsDAO
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        generator.generateDefaultSeason
        (
            REGIONS,
            List.of(BaseLeague.LeagueType.values()),
            QUEUE_TYPE,
            TEAM_TYPE,
            TIER_TYPE,
            TEAMS_PER_LEAGUE
        );
        leagueStatsDAO.calculateForSeason(DEFAULT_SEASON_ID);
    }

    @AfterAll
    public static void afterAll(@Autowired DataSource dataSource)
        throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }

    }

    //skip masters league for test
    @CsvSource({"1, 279", "2, 199", "3, 159", "4, 119", "5, 79", "6, 39"})
    @ParameterizedTest
    public void test4v4Ladder(int page, int teamId)
    {
        search.setResultsPerPage(REGIONS.size() * TEAMS_PER_LEAGUE);
        PagedSearchResult<List<LadderTeam>> result = search.find
        (
            DEFAULT_SEASON_ID,
            EnumSet.copyOf(REGIONS),
            LEAGUES_SET,
            QUEUE_TYPE,
            TEAM_TYPE,
            page
        );
        verifyLadder(result, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE, page, teamId);
    }

    //skip masters league for test
    @CsvSource({"1, 279", "2, 199", "3, 159", "4, 119", "5, 79", "6, 39"})
    @ParameterizedTest
    public void test4v4LadderAnchor(int page, int teamId)
    {
        int leagueTeamCount = REGIONS.size() * TEAMS_PER_LEAGUE;
        search.setResultsPerPage(leagueTeamCount);
        PagedSearchResult<List<LadderTeam>> result = search.findAnchored
        (
            DEFAULT_SEASON_ID,
            EnumSet.copyOf(REGIONS),
            LEAGUES_SET,
            QUEUE_TYPE,
            TEAM_TYPE,
            page - 1,
            //team id is zero based in generator, but actual db id is 1 based. + 2 to offset that
            teamId, teamId + 2,
            true,
            1
        );
        verifyLadder(result, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE, page, teamId);

        //reversed
        PagedSearchResult<List<LadderTeam>> resultReversed = search.findAnchored
        (
            DEFAULT_SEASON_ID,
            EnumSet.copyOf(REGIONS),
            LEAGUES_SET,
            QUEUE_TYPE,
            TEAM_TYPE,
            page + 1,
            //for reversed order the generator offset is correct
            teamId - leagueTeamCount, (teamId - leagueTeamCount) + 1,
            false,
            1
        );
        verifyLadder(resultReversed, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE, page, teamId);
    }

    @Test
    public void test4v4LadderAnchorMultiplePages()
    {
        int leagueTeamCount = REGIONS.size() * TEAMS_PER_LEAGUE;
        search.setResultsPerPage(leagueTeamCount);
        PagedSearchResult<List<LadderTeam>> result = search.findAnchored
        (
            DEFAULT_SEASON_ID,
            EnumSet.copyOf(REGIONS),
            LEAGUES_SET,
            QUEUE_TYPE,
            TEAM_TYPE,
            1,
            200, 201,
            true,
            2
        );
        verifyLadder(result, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE, 3, 159);

        //reversed
        PagedSearchResult<List<LadderTeam>> resultReversed = search.findAnchored
        (
            DEFAULT_SEASON_ID,
            EnumSet.copyOf(REGIONS),
            LEAGUES_SET,
            QUEUE_TYPE,
            TEAM_TYPE,
            3,
            //for reversed order the generator offset is correct
            159, 160,
            false,
            2
        );
        verifyLadder(resultReversed, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE, 1, 279);
    }

    private void verifyLadder
    (
        PagedSearchResult<List<LadderTeam>> result,
        QueueType queueType,
        TeamType teamType,
        BaseLeagueTier.LeagueTierType tierType,
        int page,
        int teamId
    )
    {
        int expectedTeamCount = REGIONS.size() * SEARCH_LEAGUES.size() * TEAMS_PER_LEAGUE;
        int leagueTeamCount = REGIONS.size() * TEAMS_PER_LEAGUE;

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
            team.getMembers().sort(Comparator.comparing(m->m.getCharacter().getBattlenetId()));
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
        int teamsPerPage = 45;
        int lastPage = 6;

        search.setResultsPerPage(teamsPerPage);
        PagedSearchResult<List<LadderTeam>> result = search.find
        (
            DEFAULT_SEASON_ID,
            Set.of(Region.values()),
            LEAGUES_SET,
            QUEUE_TYPE,
            TEAM_TYPE,
            lastPage
        );

        verifyLadderOffset(result, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE);
    }

    @Test
    public void test4v4AnchoredReversedOffset()
    {
        int teamsPerPage = 45;
        int lastPage = 6;

        search.setResultsPerPage(teamsPerPage);
        PagedSearchResult<List<LadderTeam>> result = search.findAnchored
        (
            DEFAULT_SEASON_ID,
            Set.of(Region.values()),
            LEAGUES_SET,
            QUEUE_TYPE,
            TEAM_TYPE,
            lastPage + 1,
            -1,
            0,
            false,
            1
        );

        verifyLadderOffset(result, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE);
    }

    private void verifyLadderOffset
    (
        PagedSearchResult<List<LadderTeam>> result,
        QueueType queueType,
        TeamType teamType,
        BaseLeagueTier.LeagueTierType tierType
    )
    {
        int teamsPerPage = 45;
        int expectedTeamCount = REGIONS.size() * SEARCH_LEAGUES.size() * TEAMS_PER_LEAGUE;
        int leagueTeamCount = REGIONS.size() * TEAMS_PER_LEAGUE;
        int lastPage = 6;
        int leftovers = 15;
        int teamId = expectedTeamCount - (expectedTeamCount - leftovers) - 1;

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
            team.getMembers().sort(Comparator.comparing(m->m.getCharacter().getBattlenetId()));
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
        MergedLadderSearchStatsResult stats = search.findStats
        (
            DEFAULT_SEASON_ID,
            Set.of(Region.values()),
            LEAGUES_SET,
            QUEUE_TYPE,
            TEAM_TYPE
        );

        int teamCount = REGIONS.size() * SEARCH_LEAGUES.size() * TEAMS_PER_LEAGUE;
        int regionTeamCount = SEARCH_LEAGUES.size() * TEAMS_PER_LEAGUE;
        int regionPlayerCount = regionTeamCount * QUEUE_TYPE.getTeamFormat().getMemberCount(TEAM_TYPE);
        int regionGamesPlayed = (regionTeamCount * 1 + regionTeamCount * 2 + regionTeamCount * 3 + regionTeamCount * 4)
            * QUEUE_TYPE.getTeamFormat().getMemberCount(TEAM_TYPE);
        int leagueTeamCount = REGIONS.size() * TEAMS_PER_LEAGUE;
        int leaguePlayerCount = leagueTeamCount * QUEUE_TYPE.getTeamFormat().getMemberCount(TEAM_TYPE);
        int leagueGamesPlayed = (leagueTeamCount * 1 + leagueTeamCount * 2 + leagueTeamCount * 3 + leagueTeamCount * 4)
            * QUEUE_TYPE.getTeamFormat().getMemberCount(TEAM_TYPE);
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
            assertEquals((race.ordinal() + 1) * teamCount * QUEUE_TYPE.getTeamFormat().getMemberCount(TEAM_TYPE), stats.getRaceGamesPlayed().get(race));
        }

    }

}
