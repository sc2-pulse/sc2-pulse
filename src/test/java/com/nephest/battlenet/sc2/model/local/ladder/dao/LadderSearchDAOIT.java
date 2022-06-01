// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import static com.nephest.battlenet.sc2.model.local.SeasonGenerator.DEFAULT_SEASON_END;
import static com.nephest.battlenet.sc2.model.local.SeasonGenerator.DEFAULT_SEASON_ID;
import static com.nephest.battlenet.sc2.model.local.SeasonGenerator.DEFAULT_SEASON_NUMBER;
import static com.nephest.battlenet.sc2.model.local.SeasonGenerator.DEFAULT_SEASON_START;
import static com.nephest.battlenet.sc2.model.local.SeasonGenerator.DEFAULT_SEASON_YEAR;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Division;
import com.nephest.battlenet.sc2.model.local.QueueStats;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.TeamMember;
import com.nephest.battlenet.sc2.model.local.TeamState;
import com.nephest.battlenet.sc2.model.local.dao.DivisionDAO;
import com.nephest.battlenet.sc2.model.local.dao.LeagueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.PopulationStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.QueueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamState;
import com.nephest.battlenet.sc2.model.local.ladder.MergedLadderSearchStatsResult;
import com.nephest.battlenet.sc2.model.local.ladder.PagedSearchResult;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;


@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class LadderSearchDAOIT
{

    public static final int TEAMS_PER_LEAGUE = 10;
    public static final List<Region> REGIONS = Collections.unmodifiableList(List.of(Region.values()));
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
    public static final int TEAMS_PER_REGION = (BaseLeague.LeagueType.values()).length * TEAMS_PER_LEAGUE;
    public static final int TEAMS_PER_LEAGUE_REGION = REGIONS.size() * TEAMS_PER_LEAGUE;
    public static final int TEAMS_TOTAL = REGIONS.size() * (BaseLeague.LeagueType.values()).length * TEAMS_PER_LEAGUE;
    public static final int PLAYERS_TOTAL = TEAMS_TOTAL * QUEUE_TYPE.getTeamFormat().getMemberCount(TEAM_TYPE);

    @Autowired
    private LadderSearchDAO search;

    @Autowired
    private LadderStatsDAO ladderStatsDAO;

    @Autowired
    private SeasonDAO seasonDAO;

    @Autowired
    private QueueStatsDAO queueStatsDAO;

    @Autowired
    private LadderTeamStateDAO ladderTeamStateDAO;

    @BeforeAll
    public static void beforeAll
    (
        @Autowired DataSource dataSource,
        @Autowired SeasonGenerator generator,
        @Autowired QueueStatsDAO queueStatsDAO,
        @Autowired LeagueStatsDAO leagueStatsDAO,
        @Autowired SeasonDAO seasonDAO,
        @Autowired DivisionDAO divisionDAO,
        @Autowired TeamDAO teamDAO,
        @Autowired TeamMemberDAO teamMemberDAO,
        @Autowired TeamStateDAO teamStateDAO,
        @Autowired PopulationStateDAO populationStateDAO,
        @Autowired JdbcTemplate template
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }

        List<Season> seasons = new ArrayList<>();
        for(Region region : REGIONS) seasons.add(new Season(
            null, DEFAULT_SEASON_ID, region,
            DEFAULT_SEASON_YEAR, DEFAULT_SEASON_NUMBER,
            DEFAULT_SEASON_START, DEFAULT_SEASON_END
        ));
        for(Region region : REGIONS) seasons.add(new Season(
            null, DEFAULT_SEASON_ID + 1, region,
            DEFAULT_SEASON_YEAR + 1, DEFAULT_SEASON_NUMBER + 1,
            DEFAULT_SEASON_START.plusMonths(1), DEFAULT_SEASON_END.plusMonths(1)));
        List<Season> emptySeasons = new ArrayList<>();
        for(Region region : REGIONS) emptySeasons.add(new Season(null, DEFAULT_SEASON_ID + 2, region,
            DEFAULT_SEASON_YEAR + 2, DEFAULT_SEASON_NUMBER + 2,
            DEFAULT_SEASON_START.plusMonths(2), DEFAULT_SEASON_END.plusMonths(2)));

        generator.generateSeason
        (
            seasons,
            List.of(BaseLeague.LeagueType.values()),
            List.of(QUEUE_TYPE),
            TEAM_TYPE,
            TIER_TYPE,
            TEAMS_PER_LEAGUE
        );
        generator.generateSeason
        (
            emptySeasons,
            List.of(BaseLeague.LeagueType.values()),
            List.of(QUEUE_TYPE),
            TEAM_TYPE,
            TIER_TYPE,
            0
        );

        //a bunch of empty seasons for season finder testing
        seasonDAO.merge(new Season(null, 10, Region.EU, 2020, 1,
            DEFAULT_SEASON_START.plusMonths(3), DEFAULT_SEASON_END.plusMonths(3)));
        seasonDAO.merge(new Season(null, 11, Region.US, 2020, 2,
            DEFAULT_SEASON_START.plusMonths(4), DEFAULT_SEASON_END.plusMonths(4)));
        seasonDAO.merge(new Season(null, 11, Region.EU, 2020, 2,
            DEFAULT_SEASON_START.plusMonths(4), DEFAULT_SEASON_END.plusMonths(4)));

        //a team with OLD players, should not be included in the queue_stats player_base.
        //counting only NEW players.
        Division bronzeDivision = divisionDAO.findListByLadder(emptySeasons.get(0).getBattlenetId(), Region.EU,
            BaseLeague.LeagueType.BRONZE, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE).get(0);
        BaseLeague bronzeLeague = new BaseLeague(BaseLeague.LeagueType.BRONZE, QUEUE_TYPE, TEAM_TYPE);
        Team newTeam = new Team
        (
            null, emptySeasons.get(0).getBattlenetId(), Region.EU, bronzeLeague, TIER_TYPE,
            BigInteger.valueOf(9999L), bronzeDivision.getId(),
            1L, 1, 1, 1, 1
        );
        Team team = teamDAO.create(newTeam);
        //old player
        teamMemberDAO.create(new TeamMember(team.getId(), 1L, 1, 2, 3, 4));

        teamDAO.updateRanks(DEFAULT_SEASON_ID);
        teamDAO.updateRanks(DEFAULT_SEASON_ID + 1);
        teamDAO.updateRanks(DEFAULT_SEASON_ID + 2);
        leagueStatsDAO.calculateForSeason(DEFAULT_SEASON_ID);
        leagueStatsDAO.mergeCalculateForSeason(DEFAULT_SEASON_ID);
        leagueStatsDAO.calculateForSeason(DEFAULT_SEASON_ID + 1);
        leagueStatsDAO.calculateForSeason(DEFAULT_SEASON_ID + 2);
        //recreate snapshots with ranks
        template.update("DELETE FROM team_state");
        populationStateDAO.takeSnapshot(List.of(DEFAULT_SEASON_ID, DEFAULT_SEASON_ID + 1, DEFAULT_SEASON_ID + 2));
        teamStateDAO.takeSnapshot(
            LongStream.rangeClosed(1, TEAMS_TOTAL).boxed().collect(Collectors.toList()));
        queueStatsDAO.calculateForSeason(DEFAULT_SEASON_ID);
        queueStatsDAO.mergeCalculateForSeason(DEFAULT_SEASON_ID);
        queueStatsDAO.calculateForSeason(DEFAULT_SEASON_ID + 1);
        queueStatsDAO.calculateForSeason(DEFAULT_SEASON_ID + 2);
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
            1
        );
        verifyLadder(result, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE, page, teamId, true);

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
            -1
        );
        verifyLadder(resultReversed, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE, page, teamId, true);
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
            2
        );
        verifyLadder(result, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE, 3, 159, true);

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
            -2
        );
        verifyLadder(resultReversed, QUEUE_TYPE, TEAM_TYPE, TIER_TYPE, 1, 279, true);
    }

    private void verifyLadder
    (
        PagedSearchResult<List<LadderTeam>> result,
        QueueType queueType,
        TeamType teamType,
        BaseLeagueTier.LeagueTierType tierType,
        int page,
        int teamId,
        boolean cursor
    )
    {
        long expectedTeamCount = REGIONS.size() * SEARCH_LEAGUES.size() * TEAMS_PER_LEAGUE;
        int leagueTeamCount = REGIONS.size() * TEAMS_PER_LEAGUE;

        //validate meta
        assertEquals(cursor ? null : expectedTeamCount, result.getMeta().getTotalCount());
        assertEquals(leagueTeamCount, result.getMeta().getPerPage());
        assertEquals(cursor ? null :  (long) Math.ceil(expectedTeamCount / (double) leagueTeamCount), result.getMeta().getPageCount());
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
            assertEquals(tierType, team.getTierType());
            assertEquals(teamId, team.getRating());
            assertEquals(teamId, team.getWins());
            assertEquals(teamId + 1, team.getLosses());
            assertEquals(teamId + 2, team.getTies());
            TeamState state = ladderTeamStateDAO
                .find(Set.of(TeamLegacyUid.of(team))).stream()
                .map(LadderTeamState::getTeamState)
                .findAny()
                .orElseThrow();
            verifyTeamRanks(team, state, 1);
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

    private void verifyTeamRanks(LadderTeam team, TeamState state, int seasonOrdinal)
    {
        long expectedGlobalRank = (TEAMS_TOTAL - team.getId() + 1) / seasonOrdinal;
        long expectedLeagueRank = (TEAMS_PER_LEAGUE_REGION - ((team.getId() - 1) % TEAMS_PER_LEAGUE_REGION)) / seasonOrdinal;
        long expectedRegionRank =
            (((TEAMS_TOTAL - team.getId()) / TEAMS_PER_LEAGUE_REGION) * TEAMS_PER_LEAGUE //prev region ranks
            + expectedLeagueRank //cur region ranks
            - (REGIONS.size() - 1 - REGIONS.indexOf(team.getRegion())) * TEAMS_PER_LEAGUE) //region offset
            / seasonOrdinal;

        assertEquals(expectedGlobalRank, (long) team.getGlobalRank());
        assertEquals(expectedRegionRank, (long) team.getRegionRank());
        assertEquals(expectedLeagueRank, (long) team.getLeagueRank());

        assertEquals(expectedGlobalRank, (long) state.getGlobalRank());
        assertEquals(TEAMS_TOTAL, (long) state.getGlobalTeamCount());
        assertEquals(expectedRegionRank, (long) state.getRegionRank());
        assertEquals(TEAMS_PER_REGION, (long) state.getRegionTeamCount());
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
            assertEquals(tierType, team.getTierType());
            assertEquals(teamId, team.getRating());
            assertEquals(teamId, team.getWins());
            assertEquals(teamId + 1, team.getLosses());
            assertEquals(teamId + 2, team.getTies());
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
        Map<Integer, MergedLadderSearchStatsResult> statsMap = ladderStatsDAO.findStats
        (
            Set.of(Region.values()),
            LEAGUES_SET,
            QUEUE_TYPE,
            TEAM_TYPE
        );
        assertEquals(3, statsMap.size());

        int teamCount = REGIONS.size() * SEARCH_LEAGUES.size() * TEAMS_PER_LEAGUE;
        int regionTeamCount = SEARCH_LEAGUES.size() * TEAMS_PER_LEAGUE;
        int regionGamesPlayed = (regionTeamCount + regionTeamCount * 2 + regionTeamCount * 3 + regionTeamCount * 4)
            * QUEUE_TYPE.getTeamFormat().getMemberCount(TEAM_TYPE);
        int leagueTeamCount = REGIONS.size() * TEAMS_PER_LEAGUE;
        int leagueGamesPlayed = (leagueTeamCount + leagueTeamCount * 2 + leagueTeamCount * 3 + leagueTeamCount * 4)
            * QUEUE_TYPE.getTeamFormat().getMemberCount(TEAM_TYPE);
        List<Integer> sortedSeasons = statsMap.keySet().stream().sorted().collect(Collectors.toList());

        for(int i = 0; i < statsMap.size() - 1; i++)
        {
            MergedLadderSearchStatsResult stats = statsMap.get(sortedSeasons.get(i));
            for(Region region : REGIONS)
            {

                assertEquals(regionTeamCount, stats.getRegionTeamCount().get(region));
                assertEquals(regionGamesPlayed, stats.getRegionGamesPlayed().get(region));
            }
            for(BaseLeague.LeagueType league : SEARCH_LEAGUES)
            {
                assertEquals(leagueTeamCount, stats.getLeagueTeamCount().get(league));
                assertEquals(leagueGamesPlayed, stats.getLeagueGamesPlayed().get(league));
            }
            for(Race race : Race.values())
            {
                assertEquals((race.ordinal() + 1) * teamCount * QUEUE_TYPE.getTeamFormat().getMemberCount(TEAM_TYPE), stats.getRaceGamesPlayed().get(race));
            }
        }

    }

    @Test
    public void testQueueStats()
    {
        List<QueueStats> queueStats = ladderStatsDAO.findQueueStats(QUEUE_TYPE, TEAM_TYPE);
        assertEquals(3, queueStats.size());

        for(int i = 0; i < queueStats.size() - 1; i++)
        {
            assertEquals(PLAYERS_TOTAL * (i + 1), queueStats.get(i).getPlayerBase());
            assertEquals(PLAYERS_TOTAL, queueStats.get(i).getPlayerCount());
        }

        assertEquals(1, queueStats.get(2).getPlayerCount());
        //last season consists of old players, so values should be the same
        assertEquals(queueStats.get(1).getPlayerBase(), queueStats.get(2).getPlayerBase());

        assertEquals(12, queueStats.get(0).getLowActivityPlayerCount());
        assertEquals(28, queueStats.get(0).getMediumActivityPlayerCount());
        assertEquals(PLAYERS_TOTAL - 28 - 12, queueStats.get(0).getHighActivityPlayerCount());

        for(Region region : REGIONS) seasonDAO.merge(new Season(
            null, DEFAULT_SEASON_ID, region,
            DEFAULT_SEASON_YEAR, DEFAULT_SEASON_NUMBER, LocalDate.now().minusDays(15), LocalDate.now().plusDays(15)
        ));

        queueStatsDAO.mergeCalculateForSeason(DEFAULT_SEASON_ID);
        List<QueueStats> queueStatsCur = ladderStatsDAO.findQueueStats(QUEUE_TYPE, TEAM_TYPE);
        assertEquals(4, queueStatsCur.get(0).getLowActivityPlayerCount());
        assertEquals(16, queueStatsCur.get(0).getMediumActivityPlayerCount());
        assertEquals(PLAYERS_TOTAL - 4 - 16, queueStatsCur.get(0).getHighActivityPlayerCount());

        for(Region region : REGIONS) seasonDAO.merge(new Season(
            null, DEFAULT_SEASON_ID, region,
            DEFAULT_SEASON_YEAR, DEFAULT_SEASON_NUMBER, DEFAULT_SEASON_START, DEFAULT_SEASON_END
        ));

    }

    @Test
    public void testLeagueTierBounds()
    {
        Map<Region, Map<BaseLeague.LeagueType, Map<BaseLeagueTier.LeagueTierType, Integer[]>>> bounds =
            ladderStatsDAO.findLeagueBounds(DEFAULT_SEASON_ID, Set.of(Region.values()), LEAGUES_SET, QUEUE_TYPE, TEAM_TYPE);
        for(Region region : Region.values())
        {
            for(BaseLeague.LeagueType leagueType : LEAGUES_SET)
            {
                Integer[] curBounds = bounds.get(region).get(leagueType).get(TIER_TYPE);
                assertEquals(2, curBounds.length);
                assertEquals(region.ordinal() + leagueType.ordinal(), curBounds[0]);
                assertEquals(region.ordinal() + leagueType.ordinal() + 1, curBounds[1]);
            }
        }
    }

    @Test
    public void testFindSeasons()
    {
        List<Season> seasons = search.findSeasonList();

        assertEquals(5, seasons.size());

        assertEquals(DEFAULT_SEASON_ID, seasons.get(4).getBattlenetId());
        assertEquals(DEFAULT_SEASON_YEAR, seasons.get(4).getYear());
        assertEquals(DEFAULT_SEASON_NUMBER, seasons.get(4).getNumber());
        assertEquals(DEFAULT_SEASON_START, seasons.get(4).getStart());
        assertEquals(DEFAULT_SEASON_END, seasons.get(4).getEnd());

        assertEquals(DEFAULT_SEASON_ID + 1, seasons.get(3).getBattlenetId());
        assertEquals(DEFAULT_SEASON_YEAR + 1, seasons.get(3).getYear());
        assertEquals(DEFAULT_SEASON_NUMBER + 1, seasons.get(3).getNumber());
        assertEquals(DEFAULT_SEASON_START.plusMonths(1), seasons.get(3).getStart());
        assertEquals(DEFAULT_SEASON_END.plusMonths(1), seasons.get(3).getEnd());

        assertEquals(DEFAULT_SEASON_ID + 2, seasons.get(2).getBattlenetId());
        assertEquals(DEFAULT_SEASON_YEAR + 2, seasons.get(2).getYear());
        assertEquals(DEFAULT_SEASON_NUMBER + 2, seasons.get(2).getNumber());
        assertEquals(DEFAULT_SEASON_START.plusMonths(2), seasons.get(2).getStart());
        assertEquals(DEFAULT_SEASON_END.plusMonths(2), seasons.get(2).getEnd());

        assertEquals(10, seasons.get(1).getBattlenetId());
        assertEquals(2020, seasons.get(1).getYear());
        assertEquals(1, seasons.get(1).getNumber());
        assertEquals(DEFAULT_SEASON_START.plusMonths(3), seasons.get(1).getStart());
        assertEquals(DEFAULT_SEASON_END.plusMonths(3), seasons.get(1).getEnd());

        assertEquals(11, seasons.get(0).getBattlenetId());
        assertEquals(2020, seasons.get(0).getYear());
        assertEquals(2, seasons.get(0).getNumber());
        assertEquals(DEFAULT_SEASON_START.plusMonths(4), seasons.get(0).getStart());
        assertEquals(DEFAULT_SEASON_END.plusMonths(4), seasons.get(0).getEnd());
    }

}
