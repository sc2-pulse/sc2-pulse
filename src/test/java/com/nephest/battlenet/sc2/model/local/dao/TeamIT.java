// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.BasicEntityOperations;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.StatefulBasicEntityOperations;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.TeamState;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.apache.commons.lang3.SerializationUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class TeamIT
{

    @Autowired
    private TeamDAO teamDAO;

    @Autowired
    private TeamStateDAO teamStateDAO;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private JdbcTemplate template;

    private static List<BasicEntityOperations<Team>> operations;

    @BeforeAll
    public static void beforeAll(@Autowired List<BasicEntityOperations<Team>> operations)
    {
        TeamIT.operations = operations;
    }

    @BeforeEach
    public void beforeEach(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
    }

    @AfterEach
    public void afterEach
    (
        @Autowired DataSource dataSource,
        @Autowired List<StatefulBasicEntityOperations<Team>> statefulBasicEntityOperations
    )
    throws SQLException
    {
        for(StatefulBasicEntityOperations<Team> ops : statefulBasicEntityOperations)
            for(Region region : Region.values())
                ops.clear(region);
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }

    @Test
    public void testRegionAndSeasonFinder()
    {
        seasonGenerator.generateDefaultSeason
        (
            List.of(Region.values()),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_1V1, QueueType.LOTV_2V2),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            0
        );
        Team[] teams = teamDAO.merge(new LinkedHashSet<>(List.of(
            Team.joined
            (
                null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
                new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
                BaseLeagueTier.LeagueTierType.FIRST, "1", 1,
                1L, 1, 1, 1, 1,
                SC2Pulse.offsetDateTime()
            ),
            Team.joined
            (
                null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
                new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_2V2, TeamType.ARRANGED),
                BaseLeagueTier.LeagueTierType.FIRST, "2", 1,
                1L, 1, 1, 1, 1,
                SC2Pulse.offsetDateTime()
            ),
            //different region, skip
            Team.joined
            (
                null, SeasonGenerator.DEFAULT_SEASON_ID, Region.US,
                new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
                BaseLeagueTier.LeagueTierType.FIRST, "3", 1,
                1L, 1, 1, 1, 1,
                SC2Pulse.offsetDateTime()
            ),
            //different season, skip
            Team.joined
            (
                null, SeasonGenerator.DEFAULT_SEASON_ID + 1, Region.EU,
                new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
                BaseLeagueTier.LeagueTierType.FIRST, "4", 1,
                1L, 1, 1, 1, 1,
                SC2Pulse.offsetDateTime()
            )
        )))
            .stream()
            .sorted(Comparator.comparing(Team::getId))
            .toArray(Team[]::new);

        try(Stream<Team> foundTeamStream = teamDAO.find(Region.EU, 1))
        {
            Team[] foundTeams = foundTeamStream.toArray(Team[]::new);
            assertEquals(2, foundTeams.length);
            assertEquals(teams[0], foundTeams[0]);
            assertEquals(teams[1], foundTeams[1]);
        }

        List<Long> idsFound = teamDAO.findIds(Region.EU, 1);
        idsFound.sort(Comparator.naturalOrder());
        assertEquals(List.of(teams[0].getId(), teams[1].getId()), idsFound);
    }

    @ValueSource(booleans = {false, true})
    @ParameterizedTest
    public void testLadderReset(boolean validReset)
    {
        seasonGenerator.generateDefaultSeason
        (
            List.of(Region.values()),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_1V1, QueueType.LOTV_2V2),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            0
        );
        Team team = teamDAO.merge(Set.of(
            Team.joined
            (
                null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
                new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
                BaseLeagueTier.LeagueTierType.FIRST, "1", 1,
                1L, 10, 0, 0, 1,
                SC2Pulse.offsetDateTime()
            )
        )).iterator().next();
        //invalid team state, should be used because it's the last state of the team
        teamStateDAO.takeSnapshot(List.of(team.getId()));
        //9 games in a new team, 9 minutes per game
        if(validReset) template
            .update("UPDATE team_state SET timestamp = NOW() - INTERVAL '"
                + (9 * 9) + "minutes'");
        //valid team state to ensure correct ordering
        teamStateDAO.saveState(Set.of(
            new TeamState
            (
                team.getId(),
                SC2Pulse.offsetDateTime().minusYears(1),
                1,
                8,
                3
            )
        ));

        //Player couldn't play 9 games is such a short period of time, ignore it
        assertEquals
        (
            validReset ? 1 : 0,
            teamDAO.merge(Set.of(
                Team.joined
                (
                    null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
                    new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
                    BaseLeagueTier.LeagueTierType.FIRST, "1", 1,
                    1L, 9, 0, 0, 1,
                    SC2Pulse.offsetDateTime()
                )
            )).size()
        );
    }

    @Test
    public void testFindMaxLastPlayedByRegionAndSeason()
    {
        OffsetDateTime start = SC2Pulse.offsetDateTime().minusYears(1);
        List<Season> allSeasons = new ArrayList<>(2);
        for(int i = 0; i < 2; i++)
            for(Region region : new Region[]{Region.EU, Region.US})
                allSeasons.add(new Season(null, i + 1, region, 2020, i,
                    start.plusDays(i), start.plusDays(i + 1)));
        seasonGenerator.generateSeason
        (
            allSeasons,
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            0
        );
        teamDAO.merge(Set.of(
            Team.joined
            (
                null, 1, Region.EU,
                new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
                BaseLeagueTier.LeagueTierType.FIRST, "1", 1,
                3L, 4, 5, 6, 7,
                allSeasons.get(0).getStart()
            ),
            //eu season1 max
            Team.joined
            (
                null, 1, Region.EU,
                new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
                BaseLeagueTier.LeagueTierType.FIRST, "2", 1,
                3L, 4, 5, 6, 7,
                allSeasons.get(0).getStart().plusSeconds(10)
            ),
            //US team with max last_played to verify region filter
            Team.joined
            (
                null, 1, Region.US,
                new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
                BaseLeagueTier.LeagueTierType.FIRST, "1", 2,
                3L, 4, 5, 6, 7,
                allSeasons.get(1).getStart().plusSeconds(20)
            ),
            //EU season2 team with max last_played to verify season filter
            Team.joined
            (
                null, 2, Region.EU,
                new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
                BaseLeagueTier.LeagueTierType.FIRST, "1", 3,
                3L, 4, 5, 6, 7,
                allSeasons.get(2).getStart().plusSeconds(20)
            )
        ));
        assertTrue
        (
            teamDAO.findMaxLastPlayed(Region.EU, 1).orElseThrow()
                .isEqual(allSeasons.get(0).getStart().plusSeconds(10))
        );
        assertFalse(teamDAO.findMaxLastPlayed(Region.EU, 10).isPresent());
    }

    private static Stream<Arguments> teamOperations()
    {
        return operations.stream().map(Arguments::of);
    }

    @MethodSource("teamOperations")
    @ParameterizedTest
    public void whenDivisionIdIsChanged_thenUpdate(BasicEntityOperations<Team> operations)
    {
        testMerge
        (
            operations,
            team->
            {
                team.setDivisionId(team.getDivisionId() + 1);
                team.setLastPlayed(team.getLastPlayed().plusSeconds(1));
            },
            true
        );
    }

    @MethodSource("teamOperations")
    @ParameterizedTest
    public void whenWinsIsChanged_thenUpdate(BasicEntityOperations<Team> operations)
    {
        testMerge
        (
            operations,
            team->
            {
                team.setWins(team.getWins() + 1);
                team.setLastPlayed(team.getLastPlayed().plusSeconds(1));
            },
            true
        );
    }

    @MethodSource("teamOperations")
    @ParameterizedTest
    public void whenLossesIsChanged_thenUpdate(BasicEntityOperations<Team> operations)
    {
        testMerge
        (
            operations,
            team->
            {
                team.setLosses(team.getLosses() + 1);
                team.setLastPlayed(team.getLastPlayed().plusSeconds(1));
            },
            true
        );
    }

    @MethodSource("teamOperations")
    @ParameterizedTest
    public void testMergeSecondaryProperties(BasicEntityOperations<Team> operations)
    {
        testMerge
        (
            operations,
            team->
            {
                team.setDivisionId(team.getDivisionId() + 1);
                team.setLeagueType(BaseLeague.LeagueType.DIAMOND);
                team.setTierType(BaseLeagueTier.LeagueTierType.THIRD);
                team.setRating(team.getRating() + 1);
                team.setWins(team.getWins() + 1);
                team.setLosses(team.getLosses() + 1);
                team.setLastPlayed(team.getLastPlayed().plusSeconds(1));
            },
            true
        );
    }

    @MethodSource("teamOperations")
    @ParameterizedTest
    public void whenPreviousLastPlayedIsNull_thenUpdate(BasicEntityOperations<Team> operations)
    {
        testMerge
        (
            operations,
            team->team.setLastPlayed(null),
            team->
            {
                team.setWins(team.getWins() + 1);
                team.setLastPlayed(SC2Pulse.offsetDateTime());
            },
            true
        );
    }

    @MethodSource("teamOperations")
    @ParameterizedTest
    public void whenNoNewData_thenSkip(BasicEntityOperations<Team> operations)
    {
        testMerge(operations, team->{}, false);
    }

    @MethodSource("teamOperations")
    @ParameterizedTest
    public void whenPreviousLastPlayedIsAfterCurrentLastPlayed_thenSkip(BasicEntityOperations<Team> operations)
    {
        OffsetDateTime lastPlayed = SC2Pulse.offsetDateTime().minusDays(1);
        testMerge
        (
            operations,
            team->team.setLastPlayed(lastPlayed),
            team->
            {
                team.setWins(team.getWins() + 1);
                team.setLastPlayed(lastPlayed.minusSeconds(1));
            },
            false
        );
    }

    @MethodSource("teamOperations")
    @ParameterizedTest
    public void whenPreviousLastPlayedEqualsCurrentLastPlayed_thenUpdate(BasicEntityOperations<Team> operations)
    {
        OffsetDateTime lastPlayed = SC2Pulse.offsetDateTime().minusDays(1);
        testMerge
        (
            operations,
            team->team.setLastPlayed(lastPlayed),
            team->
            {
                team.setWins(team.getWins() + 1);
                team.setLastPlayed(lastPlayed);
            },
            true
        );
    }

    @MethodSource("teamOperations")
    @ParameterizedTest
    public void whenLastPlayedIsNotAfterPreviousSeasonRegionalLastPlayed_thenSkipIt(BasicEntityOperations<Team> operations)
    {
        OffsetDateTime start = SC2Pulse.offsetDateTime().minusYears(1);
        List<Season> allSeasons = new ArrayList<>(2);
        for(int i = 0; i < 2; i++)
            for(Region region : new Region[]{Region.EU, Region.US, Region.KR})
                allSeasons.add(new Season(null, i + 1, region, 2020, i,
                    start.plusDays(i), start.plusDays(i + 1)));
        seasonGenerator.generateSeason
        (
            allSeasons,
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            0
        );

        //season1 teams
        Team team1 = Team.joined
        (
            null, 1, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST, "1", 1,
            3L, 4, 5, 6, 7,
            allSeasons.get(3).getStart().minus(TeamDAO.MIN_DURATION_BETWEEN_SEASONS) //normal
        );
        Team team2 = Team.joined
        (
            null, 1, Region.US,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST, "1", 2,
            3L, 4, 5, 6, 7,
            allSeasons.get(4).getStart().minus(TeamDAO.MIN_DURATION_BETWEEN_SEASONS) //normal
        );
        Team teamOversteppedEu = Team.joined
        (
            null, 1, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST, "3", 1,
            3L, 4, 5, 6, 7,
            allSeasons.get(3).getStart().plusSeconds(10) //oversteps next season boundaries
        );
        //larger KR overstep to ensure that only regional timestamps are taken into account
        Team teamOversteppedKr = Team.joined
        (
            null, 1, Region.KR,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST, "1", 3,
            3L, 4, 5, 6, 7,
            allSeasons.get(5).getStart().plusSeconds(20) //oversteps next season boundaries
        );
        teamDAO.merge(Set.of(team1, team2, teamOversteppedEu, teamOversteppedKr));
        if(operations instanceof StatefulBasicEntityOperations<Team> statefulBasicEntityOperations)
            for(Region region : Region.values()) statefulBasicEntityOperations.load(region, 2);

        //season2 teams
        Team team3 = Team.joined
        (
            null, 2, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST, "1", 4,
            4L, 5, 6, 7, 8,
            allSeasons.get(3).getStart()
        );
        Team team4 = Team.joined
        (
            null, 2, Region.US,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST, "1", 5,
            4L, 5, 6, 7, 8,
            allSeasons.get(4).getStart()
        );
        //team3 is not saved due to overstepped lastPlayed timestamp from teamOversteppedEu(prev season)
        Assertions.assertThat(operations.merge(Set.of(team3, team4)))
            .usingRecursiveComparison()
            .isEqualTo(Set.of(team4));
        //team4 is ok
        Assertions.assertThat(operations.find(team4).orElseThrow())
            .usingRecursiveComparison()
            .isEqualTo(team4);

        //team3 is merged with correct timestamp
        Team team3_2 = SerializationUtils.clone(team3);
        team3_2.setLastPlayed
        (
            allSeasons.get(3).getStart()
                .plusSeconds(10)
                .plus(TeamDAO.MIN_DURATION_BETWEEN_SEASONS)
        );
        Assertions.assertThat(operations.merge(Set.of(team3_2)))
            .usingRecursiveComparison()
            .isEqualTo(Set.of(team3_2));

        //move team overstepped timestamp further to test update
        Team teamOversteppedEu_2 = SerializationUtils.clone(teamOversteppedEu);
        teamOversteppedEu_2.setLastPlayed(team3.getLastPlayed().plusMinutes(1));
        teamOversteppedEu_2.setWins(teamOversteppedEu.getWins() + 2);
        teamOversteppedEu_2.setRating(teamOversteppedEu.getRating() + 2);
        assertFalse(teamDAO.merge(Set.of(teamOversteppedEu_2)).isEmpty());

        if(operations instanceof StatefulBasicEntityOperations<Team> statefulBasicEntityOperations)
        {
            teamDAO.merge(Set.of(team3_2));
            for(Region region : Region.values())
            {
                statefulBasicEntityOperations.clear(region);
                statefulBasicEntityOperations.load(region, 2);
            }
        }

        //not updated due to incorrect timestamp
        Team team3_3 = SerializationUtils.clone(team3);
        team3_3.setLastPlayed
        (
            teamOversteppedEu_2.getLastPlayed()
                .plus(TeamDAO.MIN_DURATION_BETWEEN_SEASONS)
                .minusSeconds(1)
        );
        team3_3.setWins(team3.getWins() + 3);
        team3_3.setRating(team3.getRating() + 3);
        assertTrue(operations.merge(Set.of(team3_3)).isEmpty());

        //updated with correct timestamp
        Team team3_4 = SerializationUtils.clone(team3_3);
        team3_4.setLastPlayed(team3_4.getLastPlayed().plusSeconds(1));
        team3_4.setWins(team3_4.getWins() + 4);
        team3_4.setRating(team3_4.getRating() + 4);
        assertFalse(operations.merge(Set.of(team3_4)).isEmpty());
    }

    @MethodSource("teamOperations")
    @ParameterizedTest
    public void whenPreviousJoinedIsAfterCurrentJoined_thenSkip(BasicEntityOperations<Team> operations)
    {
        testMerge
        (
            operations,
            team->
            {
                team.setWins(team.getWins() + 1);
                team.setLastPlayed(team.getLastPlayed().plusSeconds(1));
                team.setJoined(team.getJoined().minusSeconds(1));
            },
            false
        );
    }

    @MethodSource("teamOperations")
    @ParameterizedTest
    public void whenPreviousJoinedEqualsCurrentJoined_thenUpdate(BasicEntityOperations<Team> operations)
    {
        testMerge
        (
            operations,
            team->
            {
                team.setWins(team.getWins() + 1);
                team.setLastPlayed(team.getLastPlayed().plusSeconds(1));
            },
            true
        );
    }

    @MethodSource("teamOperations")
    @ParameterizedTest
    public void whenPreviousJoinedIsBeforeCurrentJoined_thenUpdate(BasicEntityOperations<Team> operations)
    {
        testMerge
        (
            operations,
            team->
            {
                team.setWins(team.getWins() + 1);
                team.setLastPlayed(team.getLastPlayed().plusSeconds(1));
                team.setJoined(team.getJoined().plusSeconds(1));
            },
            true
        );
    }

    @MethodSource("teamOperations")
    @ParameterizedTest
    public void whenPreviousJoinedIsNull_thenUpdate(BasicEntityOperations<Team> operations)
    {
        testMerge
        (
            operations,
            team->
            {
                if(!(operations instanceof FastTeamDAO))
                {
                    template.update("UPDATE team SET joined = null WHERE id = ?", team.getId());
                }
                else
                {
                    team.setJoined(null);
                }
            },
            team->
            {
                team.setWins(team.getWins() + 1);
                team.setLastPlayed(team.getLastPlayed().plusSeconds(1));
            },
            true
        );
    }

    private void testMerge
    (
        BasicEntityOperations<Team> operations,
        Consumer<Team> updateModifier,
        boolean updated
    )
    {
        testMerge(operations, null, updateModifier, updated);
    }

    private void testMerge
    (
        BasicEntityOperations<Team> operations,
        Consumer<Team> originalModifier,
        Consumer<Team> updateModifier,
        boolean updated
    )
    {
        if(!(operations instanceof FastTeamDAO))
        {
            seasonGenerator.generateDefaultSeason
            (
                List.of(Region.values()),
                List.of
                    (
                        BaseLeague.LeagueType.BRONZE,
                        BaseLeague.LeagueType.SILVER,
                        BaseLeague.LeagueType.GOLD
                    ),
                List.of(QueueType.LOTV_1V1),
                TeamType.ARRANGED,
                BaseLeagueTier.LeagueTierType.FIRST,
                0
            );
        }
        OffsetDateTime lastPlayed = SC2Pulse.offsetDateTime().minusDays(1);
        Team team = Team.joined
        (
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.SILVER, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST, "1", 2,
            3L, 4, 5, 6, 7,
            lastPlayed
        );
        if(originalModifier != null) originalModifier.accept(team);
        operations.merge(Set.of(team));
        assertFullyEquals(team, operations.find(team).orElseThrow());

        Team team1_2 = SerializationUtils.clone(team);
        updateModifier.accept(team1_2);
        Team team2 = Team.joined
        (
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.SECOND, "2", 1,
            5L, 6, 7, 8, 9,
            lastPlayed.plusSeconds(2)
        );
        assertEquals(updated ? 2 : 1, operations.merge(Set.of(team1_2, team2)).size());
        assertFullyEquals(updated ? team1_2 : team, operations.find(team).orElseThrow());
        assertFullyEquals(team2, operations.find(team2).orElseThrow()); //inserted
    }

    public static void assertFullyEquals(Team team, Team team2)
    {
        Assertions.assertThat(team2)
            .usingRecursiveComparison()
            .isEqualTo(team);
    }

    public static void verifyTeam
    (
        Team team,
        Long id,
        Integer season,
        Region region,
        BaseLeague.LeagueType leagueType,
        QueueType queueType,
        TeamType teamType,
        BaseLeagueTier.LeagueTierType leagueTierType,
        BigInteger legacyId,
        Integer divisionId,
        Long rating,
        Integer wins,
        Integer losses,
        Integer ties,
        Integer points,
        OffsetDateTime lastPlayed
    )
    {
        assertEquals(id, team.getId());
        assertEquals(season, team.getSeason());
        assertEquals(region, team.getRegion());
        assertEquals(leagueType, team.getLeagueType());
        assertEquals(queueType, team.getQueueType());
        assertEquals(teamType, team.getTeamType());
        assertEquals(leagueTierType, team.getTierType());
        assertEquals(legacyId, team.getLegacyId());
        assertEquals(divisionId, team.getDivisionId());
        assertEquals(rating, team.getRating());
        assertEquals(wins, team.getWins());
        assertEquals(losses, team.getLosses());
        assertEquals(ties, team.getTies());
        assertEquals(points, team.getPoints());
        assertTrue
        (
            team.getLastPlayed() == null && lastPlayed == null
            || team.getLastPlayed().isEqual(lastPlayed)
        );
    }



}
