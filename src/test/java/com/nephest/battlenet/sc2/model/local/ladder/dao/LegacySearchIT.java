// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.*;
import com.nephest.battlenet.sc2.model.local.dao.DivisionDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamState;
import com.nephest.battlenet.sc2.web.service.StatsService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import javax.sql.DataSource;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class LegacySearchIT
{

    @Autowired
    private LadderSearchDAO ladderSearchDAO;

    @Autowired
    private LadderTeamStateDAO ladderTeamStateDAO;

    @Autowired
    private TeamStateDAO teamStateDAO;

    @Autowired @Qualifier("sc2StatsConversionService")
    private ConversionService conversionService;

    public static final BigInteger LEGACY_ID_1 = new BigInteger("99999");
    public static final BigInteger LEGACY_ID_2 = new BigInteger("999999");
    public static final BigInteger LEGACY_ID_3 = new BigInteger("9999999");
    public static OffsetDateTime ODT;

    @BeforeAll
    public static void beforeAll
    (
        @Autowired DataSource dataSource,
        @Autowired SeasonGenerator seasonGenerator,
        @Autowired DivisionDAO divisionDAO,
        @Autowired TeamDAO teamDAO,
        @Autowired TeamMemberDAO teamMemberDAO,
        @Autowired TeamStateDAO teamStateDAO
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
            ODT = OffsetDateTime.now().minusDays(teamStateDAO.getMaxDepthDaysMain() + 1);
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
            setupTeam(QueueType.LOTV_4V4, Region.EU, 1, LEGACY_ID_1, BaseLeague.LeagueType.BRONZE, 3,
                divisionDAO, teamDAO, teamMemberDAO, teamStateDAO);
            setupTeam(QueueType.LOTV_1V1, Region.US, 1, LEGACY_ID_2, BaseLeague.LeagueType.BRONZE, 3,
                divisionDAO, teamDAO, teamMemberDAO, teamStateDAO);

            setupTeam(QueueType.LOTV_4V4,  Region.EU, 2, LEGACY_ID_1, BaseLeague.LeagueType.GOLD, 10,
                divisionDAO, teamDAO, teamMemberDAO, teamStateDAO);
            setupTeam(QueueType.LOTV_1V1, Region.US, 2, LEGACY_ID_2, BaseLeague.LeagueType.GOLD, 10,
                divisionDAO, teamDAO, teamMemberDAO, teamStateDAO);

            Team team3 = setupTeam(QueueType.LOTV_1V1, Region.US, 1, LEGACY_ID_3, BaseLeague.LeagueType.BRONZE, 3,
                divisionDAO, teamDAO, teamMemberDAO, teamStateDAO);
            team3.setRating(0L);
            teamStateDAO.saveState(TeamState.of(team3, ODT.minusSeconds(1)));
            //should be removed from the archive as not a min/max state
            team3.setRating(200L);
            teamStateDAO.saveState(TeamState.of(team3, ODT.minusSeconds(2)));
            team3.setRating(300L);
            teamStateDAO.saveState(TeamState.of(team3, ODT.minusSeconds(3)));
        }
    }

    public static Team setupTeam
    (
        QueueType queueType, Region region, int season, BigInteger legacyId, BaseLeague.LeagueType league, int wins,
        DivisionDAO divisionDAO, TeamDAO teamDAO, TeamMemberDAO teamMemberDAO, TeamStateDAO teamStateDAO
    )
    {
        Division division1 = divisionDAO.findListByLadder(season, region,
            league, queueType, TeamType.ARRANGED, BaseLeagueTier.LeagueTierType.FIRST).get(0);
        Team team1 = new Team
        (
            null, season, region,
            new BaseLeague(league, queueType, TeamType.ARRANGED), BaseLeagueTier.LeagueTierType.FIRST,
            legacyId, division1.getId(),
            1L, wins, 0, 0, 1
        );
        teamDAO.merge(team1);
        TeamMember[] members = new TeamMember[queueType.getTeamFormat().getMemberCount(TeamType.ARRANGED)];
        for(int i = 0; i < members.length; i++) members[i] =
            new TeamMember(team1.getId(), i + 1L, wins, 0, 0, 0);
        teamMemberDAO.merge(members);
        teamStateDAO.saveState(TeamState.of(team1));
        team1.setWins(team1.getWins() + 1);
        teamDAO.merge(team1);
        teamStateDAO.saveState(TeamState.of(team1));
        return team1;
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

    @Test
    public void testLegacyFinders()
    {
        Set<TeamLegacyUid> legacyIds = Set.of
        (
            new TeamLegacyUid(QueueType.LOTV_4V4, Region.EU, LEGACY_ID_1),
            new TeamLegacyUid(QueueType.LOTV_1V1, Region.US, LEGACY_ID_2)
        );
        List<LadderTeam> teams = ladderSearchDAO.findLegacyTeams(legacyIds, true);
        assertEquals(4, teams.size());

        LadderTeam team1 = teams.get(0);
        assertEquals(1, team1.getSeason());
        assertEquals(QueueType.LOTV_4V4, team1.getQueueType());
        assertEquals(Region.EU, team1.getRegion());
        assertEquals(LEGACY_ID_1, team1.getLegacyId());
        assertEquals(4, team1.getWins());
        assertEquals(4, team1.getMembers().size());

        LadderTeam team2 = teams.get(1);
        assertEquals(1, team2.getSeason());
        assertEquals(QueueType.LOTV_1V1, team2.getQueueType());
        assertEquals(Region.US, team2.getRegion());
        assertEquals(LEGACY_ID_2, team2.getLegacyId());
        assertEquals(4, team2.getWins());
        assertEquals(1, team2.getMembers().size());

        LadderTeam team3 = teams.get(2);
        LadderTeam team4 = teams.get(3);
        verifyFirstTeams(teams.subList(2, 4));
        verifyFirstTeams(ladderSearchDAO.findLegacyTeams(legacyIds, false));

        List<LadderTeamState> states = ladderTeamStateDAO.find(legacyIds);
        assertEquals(8, states.size());

        LadderTeamState state1 = states.get(0);
        assertEquals(team1.getId(), state1.getTeamState().getTeamId());
        assertEquals(3, state1.getTeamState().getGames());
        assertEquals(BaseLeague.LeagueType.BRONZE, state1.getLeague().getType());

        LadderTeamState state2 = states.get(1);
        assertEquals(team1.getId(), state2.getTeamState().getTeamId());
        assertEquals(4, state2.getTeamState().getGames());
        assertEquals(BaseLeague.LeagueType.BRONZE, state2.getLeague().getType());

        LadderTeamState state3 = states.get(2);
        assertEquals(team2.getId(), state3.getTeamState().getTeamId());
        assertEquals(3, state3.getTeamState().getGames());
        assertEquals(BaseLeague.LeagueType.BRONZE, state3.getLeague().getType());

        LadderTeamState state4 = states.get(3);
        assertEquals(team2.getId(), state4.getTeamState().getTeamId());
        assertEquals(4, state4.getTeamState().getGames());
        assertEquals(BaseLeague.LeagueType.BRONZE, state4.getLeague().getType());

        LadderTeamState state5 = states.get(4);
        assertEquals(team3.getId(), state5.getTeamState().getTeamId());
        assertEquals(10, state5.getTeamState().getGames());
        assertEquals(BaseLeague.LeagueType.GOLD, state5.getLeague().getType());

        LadderTeamState state6 = states.get(5);
        assertEquals(team3.getId(), state6.getTeamState().getTeamId());
        assertEquals(11, state6.getTeamState().getGames());
        assertEquals(BaseLeague.LeagueType.GOLD, state6.getLeague().getType());

        LadderTeamState state7 = states.get(6);
        assertEquals(team4.getId(), state7.getTeamState().getTeamId());
        assertEquals(10, state7.getTeamState().getGames());
        assertEquals(BaseLeague.LeagueType.GOLD, state7.getLeague().getType());

        LadderTeamState state8 = states.get(7);
        assertEquals(team4.getId(), state8.getTeamState().getTeamId());
        assertEquals(11, state8.getTeamState().getGames());
        assertEquals(BaseLeague.LeagueType.GOLD, state8.getLeague().getType());

        teamStateDAO.archive(ODT.minusDays(1));
        teamStateDAO.cleanArchive(ODT.minusDays(1));
        teamStateDAO.removeExpired();
        Set<TeamLegacyUid> legacyIds3 = Set.of(
            new TeamLegacyUid(QueueType.LOTV_1V1, Region.US, LEGACY_ID_3)
        );
        List<LadderTeam> teams3 = ladderSearchDAO.findLegacyTeams(legacyIds3, true);
        assertEquals(1, teams3.size());

        LadderTeam team3_1 = teams3.get(0);
        assertEquals(1, team3_1.getSeason());
        assertEquals(QueueType.LOTV_1V1, team3_1.getQueueType());
        assertEquals(Region.US, team3_1.getRegion());
        assertEquals(LEGACY_ID_3, team3_1.getLegacyId());
        assertEquals(4, team3_1.getWins());
        assertEquals(1, team3_1.getMembers().size());

        List<LadderTeamState> states3 = ladderTeamStateDAO.find(legacyIds3);
        assertEquals(4, states3.size());

        LadderTeamState state3_1 = states3.get(0);
        assertEquals(team3_1.getId(), state3_1.getTeamState().getTeamId());
        assertEquals(4, state3_1.getTeamState().getGames());
        assertEquals(300, state3_1.getTeamState().getRating());
        assertEquals(BaseLeague.LeagueType.BRONZE, state3_1.getLeague().getType());

        LadderTeamState state3_2 = states3.get(1);
        assertEquals(team3_1.getId(), state3_2.getTeamState().getTeamId());
        assertEquals(4, state3_2.getTeamState().getGames());
        assertEquals(0, state3_2.getTeamState().getRating());
        assertEquals(BaseLeague.LeagueType.BRONZE, state3_2.getLeague().getType());

        LadderTeamState state3_3 = states3.get(2);
        assertEquals(team3_1.getId(), state3_3.getTeamState().getTeamId());
        assertEquals(3, state3_3.getTeamState().getGames());
        assertEquals(1, state3_3.getTeamState().getRating());
        assertEquals(BaseLeague.LeagueType.BRONZE, state3_3.getLeague().getType());

        LadderTeamState state3_4 = states3.get(3);
        assertEquals(team3_1.getId(), state3_4.getTeamState().getTeamId());
        assertEquals(4, state3_4.getTeamState().getGames());
        assertEquals(1, state3_4.getTeamState().getRating());
        assertEquals(BaseLeague.LeagueType.BRONZE, state3_4.getLeague().getType());

    }

    private void verifyFirstTeams(List<LadderTeam> teams)
    {
        LadderTeam team3 = teams.get(0);
        assertEquals(2, team3.getSeason());
        assertEquals(QueueType.LOTV_4V4, team3.getQueueType());
        assertEquals(Region.EU, team3.getRegion());
        assertEquals(LEGACY_ID_1, team3.getLegacyId());
        assertEquals(11, team3.getWins());
        assertEquals(4, team3.getMembers().size());

        LadderTeam team4 = teams.get(1);
        assertEquals(2, team4.getSeason());
        assertEquals(QueueType.LOTV_1V1, team4.getQueueType());
        assertEquals(Region.US, team4.getRegion());
        assertEquals(LEGACY_ID_2, team4.getLegacyId());
        assertEquals(11, team4.getWins());
        assertEquals(1, team4.getMembers().size());
    }

}
