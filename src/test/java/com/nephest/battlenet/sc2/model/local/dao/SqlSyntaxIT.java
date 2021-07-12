// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.*;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
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
    private TeamStateDAO teamStateDAO;

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

    @Autowired
    private ClanDAO clanDAO;

    @Autowired
    private VarDAO varDAO;

    @Autowired
    private PostgreSQLUtils postgreSQLUtils;

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

        leagueDAO.create(new League(null, season.getId(), League.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED));
        League league = leagueDAO.merge(new League(null, season.getId(), League.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED));
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
            .findListByLadder(40, region, League.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED, BaseLeagueTier.LeagueTierType.FIRST).get(0);
        assertEquals(division, divFound);
        Division division2 = divisionDAO.merge(new Division(null, tier.getId(), 2L));
        divisionDAO.mergeById(new Division(division2.getId(), tier2.getId(), 3L));
        Division div2Found = divisionDAO
            .findListByLadder(40, region, League.LeagueType.SILVER, QueueType.LOTV_1V1, TeamType.ARRANGED,BaseLeagueTier.LeagueTierType.SECOND).get(0);
        assertEquals(tier2.getId(), div2Found.getTierId());
        assertEquals(3L, div2Found.getBattlenetId());


        Team newTeam = new Team
        (
            null, season.getBattlenetId(), season.getRegion(), league, tier.getType(),
            BigInteger.ONE, division.getId(),
            1L, 1, 1, 1, 1
        );
        Team mergedTeam = new Team
        (
            null, season.getBattlenetId(), season.getRegion(), league, tier.getType(),
            BigInteger.ONE, division.getId(),
            2L, 2, 2, 2, 2
        );
        Team sameTeam = new Team
        (
            null, season.getBattlenetId(), season.getRegion(), league, tier.getType(),
            BigInteger.ONE, division.getId(),
            2L, 2, 2, 2, 2
        );
        Team updatedTeam = new Team
        (
            null, season.getBattlenetId(), season.getRegion(), league2, tier2.getType(),
            BigInteger.ONE, division2.getId(),
            3L, 3, 3, 3, 3
        );
        Team zergTeam = new Team
        (
            null, season.getBattlenetId(), season.getRegion(), league2, tier2.getType(),
            BigInteger.TWO, division2.getId(),
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
        assertNull(teamDAO.merge(team)); //do not update a team when games played or division is the same
        team.setDivisionId(division2.getId());
        assertNotNull(teamDAO.merge(team));
        team.setDivisionId(division.getId());
        assertNotNull(teamDAO.merge(team));
        assertNull(teamDAO.merge(sameTeam));
        teamDAO.merge(updatedTeam);
        Team foundTeam = teamDAO.findById(updatedTeam.getId()).orElse(null);
        assertEquals(updatedTeam.getId(), foundTeam.getId());
        assertNotNull(foundTeam);
        assertEquals(league2.getType(), foundTeam.getLeague().getType());
        assertEquals(league2.getQueueType(), foundTeam.getLeague().getQueueType());
        assertEquals(league2.getTeamType(), foundTeam.getLeague().getTeamType());
        assertEquals(tier2.getType(), foundTeam.getTierType());
        assertEquals(division2.getId(), foundTeam.getDivisionId());
        assertEquals(BigInteger.ONE, foundTeam.getLegacyId());
        assertEquals(3, foundTeam.getRating());
        assertEquals(3, foundTeam.getWins());
        assertEquals(3, foundTeam.getLosses());
        assertEquals(3, foundTeam.getTies());
        assertEquals(3, foundTeam.getPoints());

        Account createdAccount = accountDAO.create(new Account(null, Partition.GLOBAL, "newtag#2"));
        Account account = accountDAO.merge(new Account(null, Partition.GLOBAL, "newtag#2"));
        assertEquals(createdAccount.getId(), account.getId());
        assertEquals("newtag#2", account.getBattleTag());
        assertEquals(account, accountDAO.find(Partition.GLOBAL, "newtag#2").get());

        PlayerCharacter createdCharacter = new PlayerCharacter(null, account.getId(), season.getRegion(), 1L, 1, "name#1");
        playerCharacterDAO.create(createdCharacter);
        PlayerCharacter mergedCharacter = new PlayerCharacter(null, account.getId(), season.getRegion(), 1L, 1, "name#2");
        playerCharacterDAO.merge(mergedCharacter);
        assertEquals(playerCharacterDAO.find(createdCharacter.getRegion(), createdCharacter.getRealm(), createdCharacter.getBattlenetId())
            .get().getName(), mergedCharacter.getName());
        assertEquals(createdCharacter.getId(), mergedCharacter.getId());

        PlayerCharacter character = playerCharacterDAO
            .merge(new PlayerCharacter(null, account.getId(), season.getRegion(), 1L, 2, "newname#2"));
        assertEquals(2, character.getRealm());
        assertEquals("newname#2", character.getName());
        assertEquals(character,
            playerCharacterDAO.find(character.getRegion(), character.getRealm(), character.getBattlenetId()).get());
        Clan[] clans = new Clan[]{
            new Clan(null, "clanTag1", Region.EU, "clanName1"),
            new Clan(null, "clanTag2", Region.EU, "clanName2"),
            new Clan(null, "clanTag1", Region.EU, "clanName1")//duplicate
        };
        Clan[] mergedClans = clanDAO.merge(clans);
        assertEquals(3, mergedClans.length);
        for(Clan clan : mergedClans) assertNotNull(clan.getId());
        for(Clan clan : clans) assertNotNull(clan.getId());
        assertEquals(clans[0].getId(), clans[1].getId());
        Clan[] mergedClans2 = clanDAO.merge(
            new Clan(null, "clanTag1", Region.EU, "clanName1"), //nothing
            new Clan(null, "clanTag2", Region.EU, "clanAnotherName2"), //update
            new Clan(null, "clanTag3", Region.EU, "clanName3") //insert
        );
        assertEquals(clans[0].getId(), mergedClans2[0].getId());
        assertEquals(clans[2].getId(), mergedClans2[1].getId());

        teamMemberDAO.create(new TeamMember(zergTeam.getId(), character.getId(), 0, 0, 6, 0));
        teamMemberDAO.create(new TeamMember(team.getId(), character.getId(), 1, 1, 1, 1));
        TeamMember teamMember = teamMemberDAO.merge(new TeamMember(team.getId(), character.getId(), 8, 0, 0, 0));
        assertEquals(8, teamMember.getTerranGamesPlayed());
        assertEquals(0, teamMember.getProtossGamesPlayed());
        assertEquals(0, teamMember.getZergGamesPlayed());
        assertEquals(0, teamMember.getRandomGamesPlayed());

        assertEquals(zergTeam.getId(),
            teamDAO.find1v1TeamByFavoriteRace(40, character, Race.ZERG).get().getKey().getId());
        zergTeam.setWins(zergTeam.getWins() + 1);
        teamDAO.merge(zergTeam);
        assertEquals(zergTeam.getWins(), teamDAO.find1v1TeamByFavoriteRace(season.getBattlenetId(), character, Race.ZERG)
            .get().getKey().getWins());
        assertEquals(team.getId(),teamDAO.find1v1TeamByFavoriteRace(40, character, Race.TERRAN).get().getKey().getId());
        assertTrue(teamDAO.find1v1TeamByFavoriteRace(40, character, Race.PROTOSS).isEmpty());

        leagueStatsDAO.calculateForSeason(40);
        leagueStatsDAO.mergeCalculateForSeason(40);

        proPlayerAccountDAO.removeExpired();
        proPlayerDAO.removeExpired();
        proTeamDAO.removeExpired();
        proTeamMemberDAO.removeExpired();
        teamStateDAO.removeExpired();

        teamMemberDAO.removeByTeamId(zergTeam.getId());
        assertTrue(teamDAO.find1v1TeamByFavoriteRace(40, character, Race.ZERG).isEmpty());

        postgreSQLUtils.vacuum();
        postgreSQLUtils.analyze();
        postgreSQLUtils.vacuumAnalyze();
    }

    @Test
    public void testVar()
    {
        String key = "some. Key";
        varDAO.merge(key, "1");
        assertEquals("1", varDAO.find(key).get());
        varDAO.merge(key, "val ue]1");
        assertEquals("val ue]1", varDAO.find(key).get());
    }

}
