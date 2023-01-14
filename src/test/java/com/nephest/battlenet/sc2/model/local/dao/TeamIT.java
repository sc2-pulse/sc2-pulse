// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.TeamState;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
    public void afterEach(@Autowired DataSource dataSource)
    throws SQLException
    {
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
        Team[] teams = teamDAO.merge
        (
            new Team
            (
                null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
                new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
                BaseLeagueTier.LeagueTierType.FIRST, BigInteger.valueOf(1), 1,
                1L, 1, 1, 1, 1
            ),
            new Team
            (
                null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
                new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_2V2, TeamType.ARRANGED),
                BaseLeagueTier.LeagueTierType.FIRST, BigInteger.valueOf(2), 1,
                1L, 1, 1, 1, 1
            ),
            //different region, skip
            new Team
            (
                null, SeasonGenerator.DEFAULT_SEASON_ID, Region.US,
                new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
                BaseLeagueTier.LeagueTierType.FIRST, BigInteger.valueOf(3), 1,
                1L, 1, 1, 1, 1
            ),
            //different season, skip
            new Team
            (
                null, SeasonGenerator.DEFAULT_SEASON_ID + 1, Region.EU,
                new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
                BaseLeagueTier.LeagueTierType.FIRST, BigInteger.valueOf(4), 1,
                1L, 1, 1, 1, 1
            )
        );

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
        Team team = teamDAO.merge
        (
            new Team
            (
                null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
                new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
                BaseLeagueTier.LeagueTierType.FIRST, BigInteger.valueOf(1), 1,
                1L, 10, 0, 0, 1
            )
        )[0];
        //invalid team state, should be used because it's the last state of the team
        teamStateDAO.takeSnapshot(List.of(team.getId()));
        //9 games in a new team, 9 minutes per game
        if(validReset) template
            .update("UPDATE team_state SET timestamp = NOW() - INTERVAL '"
                + (9 * 9) + "minutes'");
        //valid team state to ensure correct ordering
        teamStateDAO.saveState
        (
            new TeamState
            (
                team.getId(),
                OffsetDateTime.now().minusYears(1),
                1,
                8,
                3
            )
        );

        //Player couldn't play 9 games is such a short period of time, ignore it
        assertEquals
        (
            validReset ? 1 : 0,
            teamDAO.merge
            (
                new Team
                (
                    null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
                    new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
                    BaseLeagueTier.LeagueTierType.FIRST, BigInteger.valueOf(1), 1,
                    1L, 9, 0, 0, 1
                )
            ).length
        );
    }

}
