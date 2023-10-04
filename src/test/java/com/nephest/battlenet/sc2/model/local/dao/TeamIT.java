// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.BasicEntityOperations;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.TeamState;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.apache.commons.lang3.SerializationUtils;
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
    public void afterEach(@Autowired DataSource dataSource, @Autowired FastTeamDAO fastTeamDAO)
    throws SQLException
    {
        for(Region region : Region.values()) fastTeamDAO.remove(region);
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
            new Team
            (
                null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
                new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
                BaseLeagueTier.LeagueTierType.FIRST, BigInteger.valueOf(1), 1,
                1L, 1, 1, 1, 1,
                OffsetDateTime.now()
            ),
            new Team
            (
                null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
                new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_2V2, TeamType.ARRANGED),
                BaseLeagueTier.LeagueTierType.FIRST, BigInteger.valueOf(2), 1,
                1L, 1, 1, 1, 1,
                OffsetDateTime.now()
            ),
            //different region, skip
            new Team
            (
                null, SeasonGenerator.DEFAULT_SEASON_ID, Region.US,
                new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
                BaseLeagueTier.LeagueTierType.FIRST, BigInteger.valueOf(3), 1,
                1L, 1, 1, 1, 1,
                OffsetDateTime.now()
            ),
            //different season, skip
            new Team
            (
                null, SeasonGenerator.DEFAULT_SEASON_ID + 1, Region.EU,
                new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
                BaseLeagueTier.LeagueTierType.FIRST, BigInteger.valueOf(4), 1,
                1L, 1, 1, 1, 1,
                OffsetDateTime.now()
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
            new Team
            (
                null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
                new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
                BaseLeagueTier.LeagueTierType.FIRST, BigInteger.valueOf(1), 1,
                1L, 10, 0, 0, 1,
                OffsetDateTime.now()
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
                OffsetDateTime.now().minusYears(1),
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
                new Team
                (
                    null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
                    new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
                    BaseLeagueTier.LeagueTierType.FIRST, BigInteger.valueOf(1), 1,
                    1L, 9, 0, 0, 1,
                    OffsetDateTime.now()
                )
            )).size()
        );
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
                team.setLastPlayed(OffsetDateTime.now());
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
        OffsetDateTime lastPlayed = OffsetDateTime.now().minusDays(1);
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
    public void whenPreviousLastPlayedEqualsCurrentLastPlayed_thenSkip(BasicEntityOperations<Team> operations)
    {
        OffsetDateTime lastPlayed = OffsetDateTime.now().minusDays(1);
        testMerge
        (
            operations,
            team->team.setLastPlayed(lastPlayed),
            team->
            {
                team.setWins(team.getWins() + 1);
                team.setLastPlayed(lastPlayed);
            },
            false
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
        OffsetDateTime lastPlayed = OffsetDateTime.now().minusDays(1);
        Team team = new Team
        (
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.SILVER, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST, BigInteger.valueOf(1), 2,
            3L, 4, 5, 6, 7,
            lastPlayed
        );
        if(originalModifier != null) originalModifier.accept(team);
        operations.merge(Set.of(team));
        assertFullyEquals(team, operations.find(team).orElseThrow());

        Team team1_2 = SerializationUtils.clone(team);
        updateModifier.accept(team1_2);
        Team team2 = new Team
        (
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.SECOND, BigInteger.valueOf(2), 1,
            5L, 6, 7, 8, 9,
            lastPlayed.plusSeconds(2)
        );
        assertEquals(updated ? 2 : 1, operations.merge(Set.of(team1_2, team2)).size());
        assertFullyEquals(updated ? team1_2 : team, operations.find(team).orElseThrow());
        assertFullyEquals(team2, operations.find(team2).orElseThrow()); //inserted
    }

    public static void assertFullyEquals(Team team, Team team2)
    {
        assertEquals(team.getId(), team2.getId());
        assertEquals(team.getSeason(), team2.getSeason());
        assertEquals(team.getRegion(), team2.getRegion());
        assertEquals(team.getLeagueType(), team2.getLeagueType());
        assertEquals(team.getQueueType(), team2.getQueueType());
        assertEquals(team.getTeamType(), team2.getTeamType());
        assertEquals(team.getTierType(), team2.getTierType());
        assertEquals(team.getLegacyId(), team2.getLegacyId());
        assertEquals(team.getDivisionId(), team2.getDivisionId());
        assertEquals(team.getRating(), team2.getRating());
        assertEquals(team.getWins(), team2.getWins());
        assertEquals(team.getLosses(), team2.getLosses());
        assertEquals(team.getTies(), team2.getTies());
        assertEquals(team.getPoints(), team2.getPoints());
        assertTrue
        (
            (team.getLastPlayed() == null && team2.getLastPlayed() == null)
            || team.getLastPlayed().isEqual(team2.getLastPlayed())
        );
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
