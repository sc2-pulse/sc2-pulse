// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import javax.sql.DataSource;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class SqlSyntaxIT
{
    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private PlayerCharacterDAO playerCharacterDAO;

    @Autowired
    private SeasonDAO seasonDAO;

    @Autowired
    private LeagueDAO leagueDAO;

    @Autowired
    private LeagueTierDAO leagueTierDAO;

    @Autowired
    private DivisionDAO divisionDAO;

    @Autowired
    private TeamDAO teamDAO;

    @Autowired
    private TeamMemberDAO teamMemberDAO;

    @Autowired
    private LeagueStatsDAO leagueStatsDAO;

    @Autowired
    private ProPlayerDAO proPlayerDAO;

    @Autowired
    private ProTeamDAO proTeamDAO;

    @Autowired
    private ProTeamMemberDAO proTeamMemberDAO;

    @Autowired
    private ProPlayerAccountDAO proPlayerAccountDAO;

    @BeforeAll
    public static void beforeAll(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
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
    public void testSqlSyntax()
    {
        Region region = Region.EU;
        seasonDAO.create(new Season(null, 40, region, 2020, 1, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1)));
        seasonDAO.merge(new Season(null, 40, region, 2019, 2, LocalDate.of(2019, 2, 1), LocalDate.of(2019, 3, 1)));
        List<Season> seasons = seasonDAO.findListByRegion(region);
        assertEquals(1, seasons.size());
        Season season = seasons.get(0);
        assertEquals(40, season.getBattlenetId());
        assertEquals(2019, season.getYear());
        assertEquals(2, season.getNumber());

        leagueDAO.create(new League(null, season.getId(), League.LeagueType.BRONZE, QueueType.HOTS_1V1, TeamType.ARRANGED));
        League league = leagueDAO.merge(new League(null, season.getId(), League.LeagueType.BRONZE, QueueType.HOTS_1V1, TeamType.ARRANGED));
        League league2 = leagueDAO.merge(new League(null, season.getId(), League.LeagueType.SILVER, QueueType.LOTV_1V1, TeamType.ARRANGED));
        leagueTierDAO.create(new LeagueTier(null, league.getId(), LeagueTier.LeagueTierType.FIRST, 0, 1));
        LeagueTier tier = leagueTierDAO.merge(new LeagueTier(null, league.getId(), LeagueTier.LeagueTierType.FIRST, 1, 2));
        LeagueTier tier2 = leagueTierDAO.merge(new LeagueTier(null, league2.getId(), LeagueTier.LeagueTierType.SECOND, 1, 2));
        assertEquals(1, tier.getMinRating());
        assertEquals(2, tier.getMaxRating());
        LeagueTier tierFound = leagueTierDAO
            .findByLadder(40, region, league.getType(), league.getQueueType(), league.getTeamType(), tier.getType()).orElse(null);
        assertNotNull(tierFound);
        assertEquals(1, tierFound.getMinRating());
        assertEquals(2, tierFound.getMaxRating());
        assertEquals(tier, tierFound);

        divisionDAO.create(new Division(null, tier.getId(), 1L));
        Division division = divisionDAO.merge(new Division(null, tier.getId(), 1L));
        Division divFound = divisionDAO
            .findListByLadder(40, region, League.LeagueType.BRONZE, QueueType.HOTS_1V1, TeamType.ARRANGED, BaseLeagueTier.LeagueTierType.FIRST).get(0);
        assertEquals(division, divFound);
        Division division2 = divisionDAO.merge(new Division(null, tier.getId(), 2L));
        divisionDAO.mergeById(new Division(division2.getId(), tier2.getId(), 3L));
        Division div2Found = divisionDAO
            .findListByLadder(40, region, League.LeagueType.SILVER, QueueType.LOTV_1V1, TeamType.ARRANGED,BaseLeagueTier.LeagueTierType.SECOND).get(0);
        assertEquals(tier2.getId(), div2Found.getTierId());
        assertEquals(3L, div2Found.getBattlenetId());


        Team newTeam = new Team
        (
            null, season.getBattlenetId(), season.getRegion(), league, tier.getType(), division.getId(), BigInteger.ONE,
            1L, 1, 1, 1, 1
        );
        Team mergedTeam = new Team
        (
            null, season.getBattlenetId(), season.getRegion(), league, tier.getType(), division.getId(), BigInteger.ONE,
            2L, 2, 2, 2, 2
        );
        Team sameTeam = new Team
        (
            null, season.getBattlenetId(), season.getRegion(), league, tier.getType(), division.getId(), BigInteger.ONE,
            2L, 2, 2, 2, 2
        );
        Team mergedByIdTeam = new Team
        (
            null, season.getBattlenetId(), season.getRegion(), league2, tier2.getType(), division2.getId(), BigInteger.TEN,
            3L, 3, 3, 3, 3
        );
        Team zergTeam = new Team
        (
            null, season.getBattlenetId(), season.getRegion(), league2, tier2.getType(), division2.getId(), BigInteger.TWO,
            4L, 3, 3, 0, 3
        );
        teamDAO.create(newTeam);
        teamDAO.create(zergTeam);
        Team team = teamDAO.merge(mergedTeam);
        assertNotNull(team.getId());
        assertEquals(2, team.getRating());
        assertEquals(2, team.getWins());
        assertEquals(2, team.getLosses());
        assertEquals(2, team.getTies());
        assertEquals(2, team.getPoints());
        assertNull(teamDAO.merge(team));
        assertNotNull(teamDAO.mergeById(team, true));
        assertNotNull(teamDAO.merge(team, true));
        assertNull(teamDAO.merge(sameTeam));
        mergedByIdTeam.setId(team.getId());
        teamDAO.mergeById(mergedByIdTeam, false);
        Team foundTeam = teamDAO.findById(mergedByIdTeam.getId()).orElse(null);
        assertEquals(mergedByIdTeam.getId(), foundTeam.getId());
        assertNotNull(foundTeam);
        assertEquals(league2.getType(), foundTeam.getLeague().getType());
        assertEquals(league2.getQueueType(), foundTeam.getLeague().getQueueType());
        assertEquals(league2.getTeamType(), foundTeam.getLeague().getTeamType());
        assertEquals(tier2.getType(), foundTeam.getTierType());
        assertEquals(division2.getId(), foundTeam.getDivisionId());
        assertEquals(BigInteger.TEN, foundTeam.getBattlenetId());
        assertEquals(3, foundTeam.getRating());
        assertEquals(3, foundTeam.getWins());
        assertEquals(3, foundTeam.getLosses());
        assertEquals(3, foundTeam.getTies());
        assertEquals(3, foundTeam.getPoints());

        accountDAO.create(new Account(null, Partition.GLOBAL, "tag#1"));
        Account account = accountDAO.merge(new Account(null, Partition.GLOBAL, "newtag#2"));
        assertEquals("newtag#2", account.getBattleTag());
        accountDAO.removeExpiredByPrivacy();

        playerCharacterDAO.create(new PlayerCharacter(null, account.getId(), season.getRegion(), 1L, 1, "name#1"));
        PlayerCharacter character = playerCharacterDAO
            .merge(new PlayerCharacter(null, account.getId(), season.getRegion(), 1L, 2, "newname#2"));
        assertEquals(2, character.getRealm());
        assertEquals("newname#2", character.getName());
        assertEquals(character,
            playerCharacterDAO.find(character.getRegion(), character.getRealm(), character.getBattlenetId()).get());

        teamMemberDAO.create(new TeamMember(zergTeam.getId(), character.getId(), 0, 0, 6, 0));
        teamMemberDAO.create(new TeamMember(team.getId(), character.getId(), 1, 1, 1, 1));
        TeamMember teamMember = teamMemberDAO.merge(new TeamMember(team.getId(), character.getId(), 8, 0, 0, 0));
        assertEquals(8, teamMember.getTerranGamesPlayed());
        assertEquals(0, teamMember.getProtossGamesPlayed());
        assertEquals(0, teamMember.getZergGamesPlayed());
        assertEquals(0, teamMember.getRandomGamesPlayed());

        assertEquals(zergTeam.getId(),
            teamDAO.find1v1TeamByFavoriteRace(40, character, Race.ZERG).get().getKey().getId());
        assertEquals(team.getId(),teamDAO.find1v1TeamByFavoriteRace(40, character, Race.TERRAN).get().getKey().getId());
        assertTrue(teamDAO.find1v1TeamByFavoriteRace(40, character, Race.PROTOSS).isEmpty());

        leagueStatsDAO.calculateForSeason(40);
        leagueStatsDAO.mergeCalculateForSeason(40);

        proPlayerAccountDAO.removeExpired();
        proPlayerDAO.removeExpired();
        proTeamDAO.removeExpired();
        proTeamMemberDAO.removeExpired();

        teamMemberDAO.removeByTeamId(zergTeam.getId());
        assertTrue(teamDAO.find1v1TeamByFavoriteRace(40, character, Race.ZERG).isEmpty());
    }

}
