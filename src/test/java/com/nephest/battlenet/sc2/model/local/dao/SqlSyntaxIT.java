// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.Division;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.LeagueTier;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.SC2Map;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.TeamMember;
import com.nephest.battlenet.sc2.model.twitch.dao.TwitchVideoDAO;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/*
    This tests contains many simple subtests that require almost no setup(things like entity merge, count, etc.).
 */

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class SqlSyntaxIT
{
    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private AccountRoleDAO accountRoleDAO;

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
    private SC2MapDAO mapDAO;

    @Autowired
    private TwitchVideoDAO twitchVideoDAO;

    @Autowired
    private VarDAO varDAO;

    @Autowired
    private PostgreSQLUtils postgreSQLUtils;

    @Autowired
    private JdbcTemplate template;

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

    /*TODO
        Split this abomination into proper tests
     */
    @Test
    public void testSqlSyntax()
    {
        Region region = Region.EU;
        Season s1 = seasonDAO.merge(new Season(null, 40, region, 2020, 1,
            SC2Pulse.offsetDateTime(2020, 1, 1), SC2Pulse.offsetDateTime(2020, 2, 1)));
        Season s2 = seasonDAO.merge(new Season(null, 40, region, 2019, 2, 
            SC2Pulse.offsetDateTime(2019, 2, 1), SC2Pulse.offsetDateTime(2019, 3, 1)));
        assertEquals(s1.getId(), s2.getId());
        List<Season> seasons = seasonDAO.findListByRegion(region);
        assertEquals(1, seasons.size());
        Season season = seasons.get(0);
        assertEquals(40, season.getBattlenetId());
        assertEquals(2019, season.getYear());
        assertEquals(2, season.getNumber());
        Season s3 = seasonDAO.merge(new Season(null, 39, region, 2019, 1,
            SC2Pulse.offsetDateTime(2019, 1, 1), SC2Pulse.offsetDateTime(2019, 2, 1)));
        assertEquals(s1, seasonDAO.findLast().orElseThrow());

        League leagueOrig = leagueDAO.merge(new League(null, season.getId(), League.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED));
        League league = leagueDAO.merge(new League(null, season.getId(), League.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED));
        assertEquals(leagueOrig.getId(), league.getId());
        League league2 = leagueDAO.merge(new League(null, season.getId(), League.LeagueType.SILVER, QueueType.LOTV_1V1, TeamType.ARRANGED));
        LeagueTier tierOrig = leagueTierDAO.merge(new LeagueTier(null, league.getId(), LeagueTier.LeagueTierType.FIRST, 0, 1));
        LeagueTier tier = leagueTierDAO.merge(new LeagueTier(null, league.getId(), LeagueTier.LeagueTierType.FIRST, 1, 2));
        assertEquals(tierOrig.getId(), tier.getId());
        leagueTierDAO.merge(new LeagueTier(null, league2.getId(), LeagueTier.LeagueTierType.SECOND, 0, 1));
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


        Team newTeam = Team.joined
        (
            null, season.getBattlenetId(), season.getRegion(), league, tier.getType(),
            "1", division.getId(),
            1L, 1, 1, 1, 1,
            SC2Pulse.offsetDateTime()
        );
        Team newTeam2 = Team.joined
        (
            null, season.getBattlenetId(), season.getRegion(), league, tier.getType(),
            "10", division.getId(),
            1L, 1, 1, 1, 1,
            SC2Pulse.offsetDateTime()
        );
        Team sameTeam = Team.joined
        (
            null, season.getBattlenetId(), season.getRegion(), league, tier.getType(),
            "1", division.getId(),
            2L, 2, 2, 2, 2,
            SC2Pulse.offsetDateTime()
        );
        Team updatedTeam = Team.joined
        (
            null, season.getBattlenetId(), season.getRegion(), league2, tier2.getType(),
            "1", division2.getId(),
            3L, 3, 3, 3, 3,
            SC2Pulse.offsetDateTime()
        );
        Team zergTeam = Team.joined
        (
            null, season.getBattlenetId(), season.getRegion(), league2, tier2.getType(),
            "-1", division2.getId(),
            4L, 3, 3, 0, 3,
            SC2Pulse.offsetDateTime()
        );
        Team zergTeamClone = Team.joined
        (
            null, season.getBattlenetId(), season.getRegion(), league2, tier2.getType(),
            "-1", division2.getId(),
            4L, 3, 3, 0, 3,
            SC2Pulse.offsetDateTime()
        );
        Team mergeTestTeam6 = createGenericTeam(season, league2, tier2, division2, "-4");
        Team mergeTestTeam5 = createGenericTeam(season, league2, tier2, division2, "-5");
        Team mergeTestTeam4 = createGenericTeam(season, league2, tier2, division2, "-6");
        Team mergeTestTeam3 = createGenericTeam(season, league2, tier2, division2, "-7");
        Team mergeTestTeam2 = createGenericTeam(season, league2, tier2, division2, "-8");
        Team mergeTestTeam1 = createGenericTeam(season, league2, tier2, division2, "-9");

        teamDAO.create(newTeam);
        teamDAO.create(zergTeam);
        teamDAO.create(mergeTestTeam2);
        teamDAO.create(mergeTestTeam3);
        //zergTeamClone is existing, same is updated, newTeam is inserted
        Team[] teams = teamDAO.merge(new LinkedHashSet<>(List.of(
                zergTeamClone, newTeam2, sameTeam)))
            .toArray(Team[]::new);
        Arrays.sort(teams, Team.NATURAL_ID_COMPARATOR);
        //existing team is excluded
        assertEquals(2, teams.length);
        assertEquals(teams[0], sameTeam);
        assertEquals(teams[1], newTeam2);
        assertNull(zergTeamClone.getId());
        assertNotNull(sameTeam.getId());
        assertNotNull(newTeam2.getId());
        assertEquals(newTeam.getId(), sameTeam.getId());
        assertNotEquals(newTeam.getId(), newTeam2.getId());

        //test correct id setting for nullable entities
        Team[] mergeTestTeams = teamDAO.merge(new LinkedHashSet<>(List.of(
            mergeTestTeam1,
            mergeTestTeam2,
            mergeTestTeam3,
            mergeTestTeam4,
            mergeTestTeam5,
            mergeTestTeam6
        )))
            .stream()
            .sorted(Team.NATURAL_ID_COMPARATOR)
            .toArray(Team[]::new);
        assertEquals(4, mergeTestTeams.length);
        assertEquals(mergeTestTeams[3], mergeTestTeam1);
        assertEquals(mergeTestTeams[2], mergeTestTeam4);
        assertEquals(mergeTestTeams[1], mergeTestTeam5);
        assertEquals(mergeTestTeams[0], mergeTestTeam6);

        Team team = teams[0];
        assertNotNull(team.getId());
        assertEquals(2, team.getRating());
        assertEquals(2, team.getWins());
        assertEquals(2, team.getLosses());
        assertEquals(2, team.getTies());
        assertEquals(2, team.getPoints());
        //do not update a team when games played or division is the same
        assertEquals(0, teamDAO.merge(Set.of(team)).size());
        team.setDivisionId(division2.getId());
        team.setLastPlayed(SC2Pulse.offsetDateTime());
        assertEquals(1, teamDAO.merge(Set.of(team)).size());
        team.setDivisionId(division.getId());
        team.setLastPlayed(SC2Pulse.offsetDateTime());
        assertEquals(1, teamDAO.merge(Set.of(team)).size());
        sameTeam.setLastPlayed(SC2Pulse.offsetDateTime());
        assertEquals(0, teamDAO.merge(Set.of(sameTeam)).size());
        sameTeam.setDivisionId(division2.getId());
        sameTeam.setLastPlayed(SC2Pulse.offsetDateTime());
        assertEquals(1, teamDAO.merge(Set.of(sameTeam)).size());
        updatedTeam.setLastPlayed(SC2Pulse.offsetDateTime());
        teamDAO.merge(Set.of(updatedTeam));
        Team foundTeam = teamDAO.findById(updatedTeam.getId()).orElse(null);
        assertEquals(updatedTeam.getId(), foundTeam.getId());
        assertNotNull(foundTeam);
        assertEquals(league2.getType(), foundTeam.getLeague().getType());
        assertEquals(league2.getQueueType(), foundTeam.getLeague().getQueueType());
        assertEquals(league2.getTeamType(), foundTeam.getLeague().getTeamType());
        assertEquals(tier2.getType(), foundTeam.getTierType());
        assertEquals(division2.getId(), foundTeam.getDivisionId());
        assertEquals("1", foundTeam.getLegacyId());
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
        Account account2 = accountDAO.merge(new Account(null, Partition.GLOBAL, "newtag#3"));

        accountRoleDAO.addRoles(account.getId(), EnumSet.of(SC2PulseAuthority.MODERATOR));
        List<SC2PulseAuthority> roles = accountRoleDAO.getRoles(account.getId());
        assertEquals(2, roles.size());
        assertTrue(roles.contains(SC2PulseAuthority.MODERATOR));
        assertTrue(roles.contains(SC2PulseAuthority.USER)); //user is a default role

        List<Account> mods = accountDAO.findByRole(SC2PulseAuthority.MODERATOR);
        assertEquals(1, mods.size());
        assertEquals(account.getId(), mods.get(0).getId());

        accountRoleDAO.addRoles(account.getId(), EnumSet.of(SC2PulseAuthority.NONE));
        List<SC2PulseAuthority> roles2 = accountRoleDAO.getRoles(account.getId());
        assertEquals(1, roles2.size());
        assertTrue(roles2.contains(SC2PulseAuthority.NONE));

        Clan[] clans = new Clan[]
        {
            new Clan(null, "clanTag1", Region.EU, "clanName1"),
            new Clan(null, "clanTag2", Region.EU, "clanName2")
        };
        Clan[] mergedClans = clanDAO.merge(new LinkedHashSet<>(Arrays.asList(clans)))
            .toArray(Clan[]::new);
        assertEquals(2, mergedClans.length);
        for(Clan clan : mergedClans) assertNotNull(clan.getId());
        for(Clan clan : clans) assertNotNull(clan.getId());
        Clan[] mergedClans2 = clanDAO.merge(new LinkedHashSet<>(List.of(
            new Clan(null, "clanTag1", Region.EU, "clanName1"), //nothing
            new Clan(null, "clanTag2", Region.EU, "clanAnotherName2"), //update
            new Clan(null, "clanTag3", Region.EU, "clanName3") //insert
        )))
            .toArray(Clan[]::new);
        Arrays.sort(mergedClans2, Clan.NATURAL_ID_COMPARATOR);
        assertEquals(clans[0].getId(), mergedClans2[0].getId());
        assertEquals(clans[1].getId(), mergedClans2[1].getId());
        assertEquals(clans[0], clanDAO.findByTag(clans[0].getTag()).get(0));

        SC2Map map2 = mapDAO.merge(Set.of(new SC2Map(null, "map2v2"))).iterator().next();
        SC2Map[] maps = mapDAO.merge(new LinkedHashSet<>(List.of(
            new SC2Map(null, "map1v1_1"), //insert
            new SC2Map(null, "map1v1_2"), //insert
            new SC2Map(null, "map2v2") //existing
        )))
            .toArray(SC2Map[]::new);
        Arrays.sort(maps, SC2Map.NATURAL_ID_COMPARATOR);
        assertEquals(3, maps.length);
        assertNotNull(maps[0].getId());
        assertNotNull(maps[1].getId());
        assertEquals(maps[2].getId(), map2.getId());

        PlayerCharacter createdCharacter = new PlayerCharacter(null, account.getId(), season.getRegion(), 1L, 1, "name#1");
        playerCharacterDAO.merge(createdCharacter);
        //update on name change
        PlayerCharacter mergedCharacter = new PlayerCharacter(null, account.getId(), season.getRegion(), 1L, 1, "name#2");
        playerCharacterDAO.merge(mergedCharacter);
        PlayerCharacter foundCharacter = playerCharacterDAO
            .find(createdCharacter.getRegion(), createdCharacter.getRealm(), createdCharacter.getBattlenetId())
            .orElseThrow();
        assertEquals(mergedCharacter.getName(), foundCharacter.getName());
        assertEquals(mergedCharacter.getAccountId(), foundCharacter.getAccountId());
        assertEquals(createdCharacter.getId(), mergedCharacter.getId());
        //update on accountId change
        mergedCharacter = new PlayerCharacter(null, account2.getId(), season.getRegion(), 1L, 1, "name#2");
        playerCharacterDAO.merge(mergedCharacter);
        assertEquals(mergedCharacter.getAccountId(),
            playerCharacterDAO.find(createdCharacter.getRegion(),createdCharacter.getRealm(), createdCharacter.getBattlenetId())
                .orElseThrow().getAccountId()
        );

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
        zergTeam.setWins(zergTeam.getWins() + 1);
        zergTeam.setLastPlayed(SC2Pulse.offsetDateTime());
        teamDAO.merge(Set.of(zergTeam));
        assertEquals(zergTeam.getWins(), teamDAO.find1v1TeamByFavoriteRace(season.getBattlenetId(), character, Race.ZERG)
            .get().getKey().getWins());
        assertEquals(team.getId(),teamDAO.find1v1TeamByFavoriteRace(40, character, Race.TERRAN).get().getKey().getId());
        assertTrue(teamDAO.find1v1TeamByFavoriteRace(40, character, Race.PROTOSS).isEmpty());

        leagueStatsDAO.calculateForSeason(40);
        leagueStatsDAO.mergeCalculateForSeason(40);

        twitchVideoDAO.removeExpired();

        teamMemberDAO.removeByTeamId(zergTeam.getId());
        assertTrue(teamDAO.find1v1TeamByFavoriteRace(40, character, Race.ZERG).isEmpty());

        postgreSQLUtils.vacuum();
        postgreSQLUtils.analyze();
        postgreSQLUtils.vacuumAnalyze();
        assertNotNull(postgreSQLUtils.getApproximateCount("team"));

        Account account3 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#123111"));
        assertEquals(1, accountDAO.removeEmptyAccounts());
        assertEquals(2, template.query("SELECT COUNT(*) FROM account", DAOUtils.INT_EXTRACTOR));
        List<Account> accs = template.query("SELECT " + AccountDAO.STD_SELECT + " FROM account ORDER BY id", AccountDAO.getStdRowMapper());
        assertEquals(account, accs.get(0));
        assertEquals(account2, accs.get(1));
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

    private static Team createGenericTeam(Season season, League league2, LeagueTier tier, Division division, String id)
    {
        return Team.joined
        (
            null, season.getBattlenetId(), season.getRegion(), league2, tier.getType(),
            id, division.getId(),
            4L, 3, 3, 0, 3,
            SC2Pulse.offsetDateTime()
        );
    }


}
