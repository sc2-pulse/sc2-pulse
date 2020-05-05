package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.Application;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringJUnitConfig(Application.class)
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
        seasonDAO.create(new Season(null, 40L, region, 2020, 1));
        seasonDAO.merge(new Season(null, 40L, region, 2019, 2));
        List<Season> seasons = seasonDAO.findListByRegion(region);
        assertEquals(1, seasons.size());
        Season season = seasons.get(0);
        assertEquals(40L, season.getBattlenetId());
        assertEquals(2019, season.getYear());
        assertEquals(2, season.getNumber());

        leagueDAO.create(new League(null, season.getId(), League.LeagueType.BRONZE, QueueType.HOTS_1V1, TeamType.ARRANGED));
        League league = leagueDAO.merge(new League(null, season.getId(), League.LeagueType.BRONZE, QueueType.HOTS_1V1, TeamType.ARRANGED));
        leagueTierDAO.create(new LeagueTier(null, league.getId(), LeagueTier.LeagueTierType.FIRST, 0, 1));
        LeagueTier tier = leagueTierDAO.merge(new LeagueTier(null, league.getId(), LeagueTier.LeagueTierType.FIRST, 1, 2));
        assertEquals(1, tier.getMinRating());
        assertEquals(2, tier.getMaxRating());

        divisionDAO.create(new Division(null, tier.getId(), 1L));
        Division division = divisionDAO.merge(new Division(null, tier.getId(), 1L));
        Division divFound = divisionDAO
            .findListByLadder(40L, region, League.LeagueType.BRONZE, QueueType.HOTS_1V1, TeamType.ARRANGED, BaseLeagueTier.LeagueTierType.FIRST).get(0);
        assertEquals(division, divFound);

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
        teamDAO.create(newTeam);
        Team team = teamDAO.merge(mergedTeam);
        assertEquals(2, team.getRating());
        assertEquals(2, team.getWins());
        assertEquals(2, team.getLosses());
        assertEquals(2, team.getTies());
        assertEquals(2, team.getPoints());

        accountDAO.create(new Account(null, "tag#1"));
        Account account = accountDAO.merge(new Account(null, "newtag#2"));
        assertEquals("newtag#2", account.getBattleTag());
        accountDAO.removeExpiredByPrivacy();

        playerCharacterDAO.create(new PlayerCharacter(null, account.getId(), season.getRegion(), 1L, 1, "name#1"));
        PlayerCharacter character = playerCharacterDAO
            .merge(new PlayerCharacter(null, account.getId(), season.getRegion(), 1L, 2, "newname#2"));
        assertEquals(2, character.getRealm());
        assertEquals("newname#2", character.getName());

        teamMemberDAO.create(new TeamMember(team.getId(), character.getId(), 1, 1, 1, 1));
        TeamMember teamMember = teamMemberDAO.merge(new TeamMember(team.getId(), character.getId(), 2, 2, 2, 2));
        assertEquals(2, teamMember.getTerranGamesPlayed());
        assertEquals(2, teamMember.getProtossGamesPlayed());
        assertEquals(2, teamMember.getZergGamesPlayed());
        assertEquals(2, teamMember.getRandomGamesPlayed());

        leagueStatsDAO.calculateForSeason(40L);
        leagueStatsDAO.mergeCalculateForSeason(40L);
    }

}
