// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.*;
import com.nephest.battlenet.sc2.web.service.BlizzardPrivacyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import javax.sql.DataSource;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class PlayerCharacterDAOIT
{

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private ClanDAO clanDAO;

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
    public void testActiveCharacterFinder()
    {
        seasonGenerator.generateDefaultSeason
        (
            List.of(Region.EU),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_2V2),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            0
        );
        Division division = divisionDAO
            .findDivision(SeasonGenerator.DEFAULT_SEASON_ID, Region.EU, QueueType.LOTV_2V2, TeamType.ARRANGED, 10)
            .get();
        Account acc1 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        Account acc2 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#2"));
        Account acc3 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#3"));
        PlayerCharacter char1 = playerCharacterDAO.merge(new PlayerCharacter(null, acc1.getId(), Region.EU, 1L, 1, "name#1"));
        PlayerCharacter char2 = playerCharacterDAO.merge(new PlayerCharacter(null, acc2.getId(), Region.EU, 2L, 2, "name#2"));
        PlayerCharacter char3 = playerCharacterDAO.merge(new PlayerCharacter(null, acc3.getId(), Region.EU, 3L, 3, "name#3"));
        Team team1 = teamDAO.merge(new Team(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_2V2, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST, new BigInteger("1"), division.getId(), 1L, 1, 1, 1, 1
        ))[0];
        Team team2 = teamDAO.merge(new Team(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_2V2, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST, new BigInteger("2"), division.getId(), 2L, 2, 2, 2, 2
        ))[0];
        teamMemberDAO.merge
        (
            new TeamMember(team1.getId(), char1.getId(), 1, 0, 0, 0),
            new TeamMember(team1.getId(), char2.getId(), 0, 1, 0, 0),
            new TeamMember(team2.getId(), char1.getId(), 0, 0, 1, 0),
            new TeamMember(team2.getId(), char3.getId(), 0, 0, 0, 1)
        );
        OffsetDateTime now = OffsetDateTime.now();
        TeamState state1 = TeamState.of(team1);
        state1.setDateTime(now.minusHours(1));
        TeamState state2 = TeamState.of(team2);
        state2.setDateTime(now.minusHours(2));
        teamStateDAO.saveState(state1, state2);

        assertTrue(playerCharacterDAO.findRecentlyActiveCharacters(now.minusMinutes(59), Region.values()).isEmpty());

        List<PlayerCharacter> search1 = playerCharacterDAO.findRecentlyActiveCharacters(now.minusHours(1), Region.values());
        search1.sort(Comparator.comparing(PlayerCharacter::getId));
        assertEquals(2, search1.size());
        assertEquals(char1, search1.get(0));
        assertEquals(char2, search1.get(1));

        List<PlayerCharacter> search2 = playerCharacterDAO.findRecentlyActiveCharacters(now.minusHours(2), Region.values());
        search2.sort(Comparator.comparing(PlayerCharacter::getId));
        assertEquals(3, search2.size());
        assertEquals(char1, search2.get(0));
        assertEquals(char2, search2.get(1));
        assertEquals(char3, search2.get(2));

        List<PlayerCharacter> search3 = playerCharacterDAO
            .findTopRecentlyActiveCharacters(now.minusHours(2), QueueType.LOTV_2V2, TeamType.ARRANGED, List.of(Region.EU), 2);
        assertEquals(2, search3.size());
        search3.sort(Comparator.comparing(PlayerCharacter::getId));
        assertEquals(char1, search3.get(0));
        assertEquals(char3, search3.get(1));
    }

    @Test
    public void updateCharacters()
    {
        Account account = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#123"));
        Clan clan = clanDAO.merge(new Clan(null, "clanTag1", Region.EU, "clanName1"))[0];
        PlayerCharacter char1 = playerCharacterDAO
            .merge(new PlayerCharacter(null, account.getId(), Region.EU, 1L, 1, "name#123", clan.getId()));
        PlayerCharacter char2 = playerCharacterDAO
            .merge(new PlayerCharacter(null, account.getId(), Region.EU, 1L, 1, "name#123", clan.getId()));
        PlayerCharacter char3 = playerCharacterDAO
            .merge(new PlayerCharacter(null, account.getId(), Region.EU, 2L, 1, "name3#123", clan.getId()));

        PlayerCharacter[] updatedCharacters = new PlayerCharacter[]
        {
            new PlayerCharacter(null, account.getId(), Region.EU, 1L, 1, "name2#123"),
            new PlayerCharacter(null, account.getId(), Region.EU, 1L, 1, "name2#123"),
            new PlayerCharacter(null, account.getId(), Region.EU, 2L, 1, "name4#123", clan.getId())
        };

        OffsetDateTime minTimeAllowed = OffsetDateTime.now();
        assertEquals(2, playerCharacterDAO.updateCharacters(updatedCharacters));

        verifyUpdatedCharacters(minTimeAllowed);
    }

    private void verifyUpdatedCharacters(OffsetDateTime minTimeAllowed)
    {
        OffsetDateTime minTime = template.query("SELECT MIN(updated) FROM player_character", DAOUtils.OFFSET_DATE_TIME_RESULT_SET_EXTRACTOR);
        assertTrue(minTime.isAfter(minTimeAllowed));

        PlayerCharacter char1 = playerCharacterDAO.find(Region.EU, 1, 1L).orElseThrow();
        PlayerCharacter char2 = playerCharacterDAO.find(Region.EU, 1, 2L).orElseThrow();
        assertEquals("name2#123", char1.getName());
        assertNull(char1.getClanId());
        assertEquals("name4#123", char2.getName());
        assertEquals(1, char2.getClanId());

        template.execute
        (
            "UPDATE player_character "
            + "SET updated = NOW() - INTERVAL '" + (BlizzardPrivacyService.DATA_TTL.toDays() + 2) +  " days' "
            + "WHERE id = 1"
        );
        playerCharacterDAO.anonymizeExpiredCharacters(OffsetDateTime.now().minusSeconds(BlizzardPrivacyService.DATA_TTL.toSeconds()).minusDays(1));
        //character is excluded due to "from' param
        assertEquals("name2#123", playerCharacterDAO.find(Region.EU, 1, 1L).orElseThrow().getName());

        playerCharacterDAO.anonymizeExpiredCharacters(OffsetDateTime.MIN);
        assertEquals(BasePlayerCharacter.DEFAULT_FAKE_FULL_NAME, playerCharacterDAO.find(Region.EU, 1, 1L).orElseThrow().getName());
    }

    @Test
    public void updateAccountsAndCharacters()
    {
        Account acc1 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#123"));
        Account acc2 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag2#123"));
        Account acc3 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag10#123"));
        Clan clan = clanDAO.merge(new Clan(null, "clanTag1", Region.EU, "clanName1"))[0];
        PlayerCharacter char1 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc1.getId(), Region.EU, 1L, 1, "name#123", clan.getId()));
        PlayerCharacter char2 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc1.getId(), Region.EU, 1L, 1, "name#123", clan.getId()));
        PlayerCharacter char3 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc2.getId(), Region.EU, 2L, 1, "name3#123", clan.getId()));
        PlayerCharacter char4 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc1.getId(), Region.EU, 3L, 1, "name10#123", clan.getId()));

        List<Tuple2<Account, PlayerCharacter>> updatedAccsAndChars = List.of
        (
            Tuples.of
            (
                new Account(null, Partition.GLOBAL, "tag3#123"),
                new PlayerCharacter(null, acc1.getId(), Region.EU, 1L, 1, "name2#123")
            ),
            Tuples.of
            (
                new Account(null, Partition.GLOBAL, "tag3#123"),
                new PlayerCharacter(null, acc1.getId(), Region.EU, 1L, 1, "name2#123")
            ),
            Tuples.of
            (
                new Account(null, Partition.GLOBAL, "tag4#123"),
                new PlayerCharacter(null, acc2.getId(), Region.EU, 2L, 1, "name4#123", clan.getId())
            ),
            Tuples.of
            (
                new Account(null, Partition.GLOBAL, "tag10#123"),
                new PlayerCharacter(null, acc1.getId(), Region.EU, 3L, 1, "name11#123")
            )
        );

        OffsetDateTime minTimeAllowed = OffsetDateTime.now();
        assertEquals(3, playerCharacterDAO.updateAccountsAndCharacters(updatedAccsAndChars));

        verifyUpdatedCharacters(minTimeAllowed);
        OffsetDateTime minAccTime =
            template.query("SELECT MIN(updated) FROM account", DAOUtils.OFFSET_DATE_TIME_RESULT_SET_EXTRACTOR);
        assertTrue(minAccTime.isAfter(minTimeAllowed));

        assertEquals("tag3#123", accountDAO.findByIds(acc1.getId()).get(0).getBattleTag());
        assertEquals("tag4#123", accountDAO.findByIds(acc2.getId()).get(0).getBattleTag());
        //character is rebound to another account
        assertEquals(acc3.getId(), playerCharacterDAO.find(Region.EU, 1, 3L).orElseThrow().getAccountId());

        template.execute
        (
            "UPDATE account "
            + "SET updated = NOW() - INTERVAL '" + (BlizzardPrivacyService.DATA_TTL.toDays() + 2) + " days' "
            + "WHERE id = " + acc1.getId()
        );
        accountDAO.anonymizeExpiredAccounts(OffsetDateTime.now().minusSeconds(BlizzardPrivacyService.DATA_TTL.toSeconds()).minusDays(1));
        //the account is excluded due to "from" param
        assertEquals("tag3#123", accountDAO.findByIds(acc1.getId()).get(0).getBattleTag());

        accountDAO.anonymizeExpiredAccounts(OffsetDateTime.MIN);
        assertEquals(BasePlayerCharacter.DEFAULT_FAKE_NAME + "#211", accountDAO.findByIds(acc1.getId()).get(0).getBattleTag());
    }

}
