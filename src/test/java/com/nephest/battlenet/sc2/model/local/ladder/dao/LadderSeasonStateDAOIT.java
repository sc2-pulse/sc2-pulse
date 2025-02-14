// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Division;
import com.nephest.battlenet.sc2.model.local.Period;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.TeamMember;
import com.nephest.battlenet.sc2.model.local.TeamState;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.DivisionDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderSeasonState;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.web.service.StatsService;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class LadderSeasonStateDAOIT
{

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private PlayerCharacterDAO playerCharacterDAO;

    @Autowired
    private DivisionDAO divisionDAO;

    @Autowired
    private TeamDAO teamDAO;

    @Autowired
    private TeamMemberDAO teamMemberDAO;

    @Autowired
    private TeamStateDAO teamStateDAO;

    @Autowired
    private SeasonStateDAO seasonStateDAO;

    @Autowired
    private LadderSeasonStateDAO ladderSeasonStateDAO;

    @BeforeAll
    public static void beforeAll
    (
        @Autowired DataSource dataSource,
        @Autowired SeasonGenerator seasonGenerator
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
            seasonGenerator.generateDefaultSeason
            (
                List.of(Region.values()),
                List.of(BaseLeague.LeagueType.values()),
                new ArrayList<>(QueueType.getTypes(StatsService.VERSION)),
                TeamType.ARRANGED,
                BaseLeagueTier.LeagueTierType.FIRST,
                0
            );
        }
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
    public void test()
    {
        Division division1 = divisionDAO
            .findListByLadder(SeasonGenerator.DEFAULT_SEASON_ID, Region.US, BaseLeague.LeagueType.BRONZE,
                QueueType.LOTV_4V4, TeamType.ARRANGED, BaseLeagueTier.LeagueTierType.FIRST)
            .get(0);
        Division division2 = divisionDAO
            .findListByLadder(SeasonGenerator.DEFAULT_SEASON_ID, Region.EU, BaseLeague.LeagueType.PLATINUM,
                QueueType.LOTV_1V1, TeamType.ARRANGED, BaseLeagueTier.LeagueTierType.FIRST)
            .get(0);
        Account account1 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        Account account2 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#2"));
        Account account3 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#3"));
        Account account4 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#4"));
        Account account5 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#5"));
        PlayerCharacter character1 = playerCharacterDAO
            .create(new PlayerCharacter(null, account1.getId(), Region.US, 1L, 1, "name#1"));
        PlayerCharacter character2 = playerCharacterDAO
            .create(new PlayerCharacter(null, account2.getId(), Region.US, 2L, 2, "name#2"));
        PlayerCharacter character3 = playerCharacterDAO
            .create(new PlayerCharacter(null, account3.getId(), Region.US, 3L, 1, "name#3"));
        PlayerCharacter character4 = playerCharacterDAO
            .create(new PlayerCharacter(null, account4.getId(), Region.US, 4L, 1, "name#4"));
        PlayerCharacter character5 = playerCharacterDAO
            .create(new PlayerCharacter(null, account5.getId(), Region.US, 5L, 2, "name#5"));
        PlayerCharacter character6 = playerCharacterDAO
            .create(new PlayerCharacter(null, account1.getId(), Region.EU, 1L, 1, "name#1"));
        Team team1 = new Team
        (
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.US,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_4V4, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST, "1", division1.getId(),
            1L, 1, 1, 1, 0,
            SC2Pulse.offsetDateTime()
        );
        teamDAO.merge(Set.of(team1));
        Team team2 = new Team
        (
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.US,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_4V4, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST, "2", division1.getId(),
            1L, 1, 1, 1, 0,
            SC2Pulse.offsetDateTime()
        );
        teamDAO.merge(Set.of(team2));
        Team team3 = new Team
        (
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.PLATINUM, QueueType.LOTV_1V1, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST, "10", division2.getId(),
            1L, 1, 1, 1, 0,
            SC2Pulse.offsetDateTime()
        );
        teamDAO.merge(Set.of(team3));
        teamMemberDAO.merge(Set.of(
            new TeamMember(team1.getId(), character1.getId(), 1, 1, 1, 0),
            new TeamMember(team1.getId(), character2.getId(), 1, 1, 1, 0),
            new TeamMember(team1.getId(), character3.getId(), 1, 1, 1, 0),
            new TeamMember(team1.getId(), character4.getId(), 1, 1, 1, 0),
            new TeamMember(team2.getId(), character1.getId(), 1, 1, 1, 0),
            new TeamMember(team2.getId(), character2.getId(), 1, 1, 1, 0),
            new TeamMember(team2.getId(), character3.getId(), 1, 1, 1, 0),
            new TeamMember(team2.getId(), character5.getId(), 1, 1, 1, 0),
            new TeamMember(team3.getId(), character6.getId(), 1, 1, 1, 0)
        ));

        //hour1: only 1 4v4 US team
        OffsetDateTime time1 = SC2Pulse.offsetDateTime().withMinute(0).withSecond(0).withNano(0);
        TeamState state1 = TeamState.of(team1);
        state1.setDateTime(time1.plusMinutes(59));
        //this state has no effect on stats
        TeamState state2 = TeamState.of(team1);
        state2.setDateTime(time1.plusSeconds(1));
        teamStateDAO.saveState(Set.of(state1, state2));
        seasonStateDAO.merge(time1, SeasonGenerator.DEFAULT_SEASON_ID);

        List<LadderSeasonState> states1 = ladderSeasonStateDAO.find(time1.plusSeconds(1), Period.DAY);
        assertEquals(1, states1.size());
        assertTrue(time1.isEqual(states1.get(0).getSeasonState().getPeriodStart()));
        assertEquals(1, states1.get(0).getSeason().getId());
        //only regions with team state for that hour
        assertEquals(6, states1.get(0).getSeasonState().getTotalGamesPlayed());
        //null if there is no previous state
        assertNull(states1.get(0).getSeasonState().getGamesPlayed());
        assertEquals(4, states1.get(0).getSeasonState().getPlayerCount());

        //hour2: 2 4v4 US teams, 1 1v1 EU team
        team1.setWins(2); // + 1 win
        team1.setLastPlayed(SC2Pulse.offsetDateTime());
        teamDAO.merge(Set.of(team1));
        OffsetDateTime time2 = time1.plusHours(1);
        TeamState state21 = TeamState.of(team1);
        state21.setDateTime(time2.plusMinutes(59));
        TeamState state22 = TeamState.of(team2);
        state22.setDateTime(time2.plusMinutes(30));
        TeamState state23 = TeamState.of(team3);
        state23.setDateTime(time2.plusSeconds(1));
        teamStateDAO.saveState(Set.of(state21, state22, state23));
        seasonStateDAO.merge(time2, SeasonGenerator.DEFAULT_SEASON_ID);

        List<LadderSeasonState> states2 = ladderSeasonStateDAO.find(time2.plusSeconds(1), Period.DAY);
        assertEquals(3, states2.size());
        assertTrue(time2.isEqual(states2.get(1).getSeasonState().getPeriodStart()));
        assertEquals(1, states2.get(1).getSeason().getId());
        assertEquals(7, states2.get(1).getSeasonState().getTotalGamesPlayed());
        assertEquals(1, states2.get(1).getSeasonState().getGamesPlayed());
        //only unique accounts
        assertEquals(5, states2.get(1).getSeasonState().getPlayerCount());

        assertTrue(time2.isEqual(states2.get(2).getSeasonState().getPeriodStart()));
        assertEquals(2, states2.get(2).getSeason().getId());
        assertEquals(3, states2.get(2).getSeasonState().getTotalGamesPlayed());
        //null if there is no previous state
        assertNull(states2.get(2).getSeasonState().getGamesPlayed());
        //only unique accounts
        assertEquals(1, states2.get(2).getSeasonState().getPlayerCount());
    }

}
