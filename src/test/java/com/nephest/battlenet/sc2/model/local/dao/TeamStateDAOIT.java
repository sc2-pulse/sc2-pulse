// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.TeamState;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamState;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderTeamStateDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LegacySearchIT;
import com.nephest.battlenet.sc2.web.service.StatsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import javax.sql.DataSource;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.nephest.battlenet.sc2.model.local.ladder.dao.LegacySearchIT.LEGACY_ID_1;
import static com.nephest.battlenet.sc2.model.local.ladder.dao.LegacySearchIT.LEGACY_ID_2;
import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class TeamStateDAOIT
{

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private TeamStateDAO teamStateDAO;

    @Autowired
    private TeamDAO teamDAO;

    @Autowired
    private TeamMemberDAO teamMemberDAO;

    @Autowired
    private DivisionDAO divisionDAO;

    @Autowired
    private LadderTeamStateDAO ladderTeamStateDAO;

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
    public void testDifferentMmrHistoryLength()
    {
        int targetLengthMain = 1000;
        int targetLengthSecondary = 500;
        int originalLengthMain = teamStateDAO.getMaxDepthDaysMain();
        int originalLengthSecondary = teamStateDAO.getMaxDepthDaysSecondary();
        teamStateDAO.setMaxDepthDaysMain(targetLengthMain);
        teamStateDAO.setMaxDepthDaysSecondary(targetLengthSecondary);

        seasonGenerator.generateSeason
        (
            List.of
            (
                new Season(null, 1, Region.EU, 2020, 1, LocalDate.now(), LocalDate.now().plusMonths(1)),
                new Season(null, 1, Region.US, 2020, 1, LocalDate.now(), LocalDate.now().plusMonths(1)),
                new Season(null, 2, Region.EU, 2020, 2, LocalDate.now(), LocalDate.now().plusMonths(2)),
                new Season(null, 2, Region.US, 2020, 2, LocalDate.now(), LocalDate.now().plusMonths(2))
            ),
            List.of(BaseLeague.LeagueType.values()),
            new ArrayList<>(QueueType.getTypes(StatsService.VERSION)),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            1
        );

        Team team1 = LegacySearchIT.setupTeam(QueueType.LOTV_4V4, Region.EU, 1, LEGACY_ID_1,
            BaseLeague.LeagueType.BRONZE, 3,
            divisionDAO, teamDAO, teamMemberDAO, teamStateDAO);
        Team team2 = LegacySearchIT.setupTeam(QueueType.LOTV_1V1, Region.US, 1, LEGACY_ID_2,
            BaseLeague.LeagueType.BRONZE, 3,
            divisionDAO, teamDAO, teamMemberDAO, teamStateDAO);

        //should be deleted as expired secondary
        TeamState state1 = TeamState.of(team1, OffsetDateTime.now().minusDays(targetLengthSecondary + 1));
        //should be saved because it's a main state
        TeamState state2Secondary = TeamState.of(team2, OffsetDateTime.now().minusDays(targetLengthSecondary + 1));
        //should be deleted as expired main
        TeamState state2Main = TeamState.of(team2, OffsetDateTime.now().minusDays(targetLengthMain + 1));
        teamStateDAO.saveState(state1, state2Secondary, state2Main);

        Set<Tuple3<QueueType, Region, BigInteger>> legacyIds1 = Set.of(
            Tuples.of(QueueType.LOTV_4V4, Region.EU, LEGACY_ID_1)
        );

        Set<Tuple3<QueueType, Region, BigInteger>> legacyIds2 = Set.of(
            Tuples.of(QueueType.LOTV_1V1, Region.US, LEGACY_ID_2)
        );

        assertEquals(3, ladderTeamStateDAO.find(legacyIds1).size());
        assertEquals(4, ladderTeamStateDAO.find(legacyIds2).size());

        teamStateDAO.removeExpired();

        List<LadderTeamState> states1 = ladderTeamStateDAO.find(legacyIds1);
        assertEquals(2, states1.size());

        assertFalse(states1.stream().map(LadderTeamState::getTeamState).anyMatch(s->s.equals(state1)));

        List<LadderTeamState> states2 = ladderTeamStateDAO.find(legacyIds2);
        assertEquals(3, states2.size());

        assertTrue(states2.stream().map(LadderTeamState::getTeamState).anyMatch(s->s.equals(state2Secondary)));
        assertFalse(states2.stream().map(LadderTeamState::getTeamState).anyMatch(s->s.equals(state2Main)));

        teamStateDAO.setMaxDepthDaysMain(originalLengthMain);
        teamStateDAO.setMaxDepthDaysSecondary(originalLengthSecondary);
    }

}
