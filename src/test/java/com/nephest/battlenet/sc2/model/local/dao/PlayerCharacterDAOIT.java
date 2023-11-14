// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.BasePlayerCharacter;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.discord.DiscordUser;
import com.nephest.battlenet.sc2.model.discord.dao.DiscordUserDAO;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.AccountDiscordUser;
import com.nephest.battlenet.sc2.model.local.AccountFollowing;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.ClanMember;
import com.nephest.battlenet.sc2.model.local.Division;
import com.nephest.battlenet.sc2.model.local.Evidence;
import com.nephest.battlenet.sc2.model.local.EvidenceVote;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterReport;
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.ProPlayerAccount;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.TeamMember;
import com.nephest.battlenet.sc2.model.local.TeamState;
import com.nephest.battlenet.sc2.web.service.BlizzardPrivacyService;
import discord4j.common.util.Snowflake;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class PlayerCharacterDAOIT
{

    @Autowired
    private ProPlayerDAO proPlayerDAO;

    @Autowired
    private ProPlayerAccountDAO proPlayerAccountDAO;

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private AccountRoleDAO accountRoleDAO;

    @Autowired
    private AccountFollowingDAO accountFollowingDAO;

    @Autowired
    private ClanDAO clanDAO;
    
    @Autowired
    private ClanMemberDAO clanMemberDAO;

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
    private PlayerCharacterReportDAO playerCharacterReportDAO;

    @Autowired
    private EvidenceDAO evidenceDAO;

    @Autowired
    private EvidenceVoteDAO evidenceVoteDAO;

    @Autowired
    private DiscordUserDAO discordUserDAO;

    @Autowired
    private AccountDiscordUserDAO accountDiscordUserDAO;

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
        Team team1 = teamDAO.merge(Set.of(new Team(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_2V2, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST, new BigInteger("1"), division.getId(), 1L, 1, 1, 1, 1,
            OffsetDateTime.now()
        ))).iterator().next();
        Team team2 = teamDAO.merge(Set.of(new Team(
            null, SeasonGenerator.DEFAULT_SEASON_ID, Region.EU,
            new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_2V2, TeamType.ARRANGED),
            BaseLeagueTier.LeagueTierType.FIRST, new BigInteger("2"), division.getId(), 2L, 2, 2, 2, 2,
            OffsetDateTime.now()
        ))).iterator().next();
        teamMemberDAO.merge(Set.of(
            new TeamMember(team1.getId(), char1.getId(), 1, 0, 0, 0),
            new TeamMember(team1.getId(), char2.getId(), 0, 1, 0, 0),
            new TeamMember(team2.getId(), char1.getId(), 0, 0, 1, 0),
            new TeamMember(team2.getId(), char3.getId(), 0, 0, 0, 1)
        ));
        OffsetDateTime now = OffsetDateTime.now();
        TeamState state1 = TeamState.of(team1);
        state1.setDateTime(now.minusHours(1));
        TeamState state2 = TeamState.of(team2);
        state2.setDateTime(now.minusHours(2));
        teamStateDAO.saveState(Set.of(state1, state2));

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
    public void testInactiveClanMembersFinder()
    {
        Account acc1 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        PlayerCharacter char1 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc1.getId(), Region.EU, 1L, 1, "name#1"));
        PlayerCharacter char2 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc1.getId(), Region.EU, 2L, 2, "name#2"));
        PlayerCharacter char3 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc1.getId(), Region.EU, 3L, 3, "name#3"));
        PlayerCharacter char4 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc1.getId(), Region.EU, 4L, 4, "name#4"));
        Clan clan1 = clanDAO.merge(Set.of(new Clan(null, "tag1", Region.EU, "name1")))
            .iterator().next();
        clanMemberDAO.merge(Set.of(new ClanMember(char1.getId(), clan1.getId())));
        clanMemberDAO.merge(Set.of(new ClanMember(char2.getId(), clan1.getId())));
        clanMemberDAO.merge(Set.of(new ClanMember(char3.getId(), clan1.getId())));
        clanMemberDAO.merge(Set.of(new ClanMember(char4.getId(), clan1.getId())));

        OffsetDateTime now = OffsetDateTime.now();

        List<PlayerCharacter> cms1 = playerCharacterDAO
            .findInactiveClanMembers(now, Long.MAX_VALUE, 2);
        assertEquals(2, cms1.size());
        assertEquals(char4, cms1.get(0));
        assertEquals(char3, cms1.get(1));

        List<PlayerCharacter> cms2 = playerCharacterDAO
            .findInactiveClanMembers(now, char3.getId(), 1);
        assertEquals(1, cms2.size());
        assertEquals(char2, cms2.get(0));

        List<PlayerCharacter> cms3 = playerCharacterDAO
            .findInactiveClanMembers(now, char2.getId(), 100);
        assertEquals(1, cms3.size());
        assertEquals(char1, cms3.get(0));

        List<PlayerCharacter> cms4 = playerCharacterDAO
            .findInactiveClanMembers(now, char1.getId(), 100);
        assertTrue(cms4.isEmpty());
    }

    @Test
    public void whenAllClanMembersAreFresh_thenReturnEmptyList()
    {
        OffsetDateTime start = OffsetDateTime.now();
        Account acc1 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        PlayerCharacter char1 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc1.getId(), Region.EU, 1L, 1, "name#1"));
        Clan clan1 = clanDAO.merge(Set.of(new Clan(null, "tag1", Region.EU, "name1")))
            .iterator().next();
        clanMemberDAO.merge(Set.of(new ClanMember(char1.getId(), clan1.getId())));

        List<PlayerCharacter> cms1 = playerCharacterDAO
            .findInactiveClanMembers(start, Long.MAX_VALUE, 100);
        assertTrue(cms1.isEmpty());
    }

    @Test
    public void updateCharacters()
    {
        Account account = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#123"));
        Clan clan = clanDAO.merge(Set.of(new Clan(null, "clanTag1", Region.EU, "clanName1")))
            .iterator().next();
        PlayerCharacter char1 = playerCharacterDAO
            .merge(new PlayerCharacter(null, account.getId(), Region.EU, 1L, 1, "name#123"));
        PlayerCharacter char2 = playerCharacterDAO
            .merge(new PlayerCharacter(null, account.getId(), Region.EU, 1L, 1, "name#123"));
        PlayerCharacter char3 = playerCharacterDAO
            .merge(new PlayerCharacter(null, account.getId(), Region.EU, 2L, 1, "name3#123"));

        Set<PlayerCharacter> updatedCharacters = Set.copyOf(List.of(
            new PlayerCharacter(null, account.getId(), Region.EU, 1L, 1, "name2#123"),
            new PlayerCharacter(null, account.getId(), Region.EU, 2L, 1, "name4#123")
        ));

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
        assertEquals("name4#123", char2.getName());

        playerCharacterDAO.updateUpdated
        (
            OffsetDateTime.now().minus(BlizzardPrivacyService.DATA_TTL.plusDays(2)),
            Set.of(1L)
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
        Clan clan = clanDAO.merge(Set.of(new Clan(null, "clanTag1", Region.EU, "clanName1")))
            .iterator().next();
        PlayerCharacter char1 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc1.getId(), Region.EU, 1L, 1, "name#123"));
        PlayerCharacter char2 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc1.getId(), Region.EU, 1L, 1, "name#123"));
        PlayerCharacter char3 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc2.getId(), Region.EU, 2L, 1, "name3#123"));
        PlayerCharacter char4 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc1.getId(), Region.EU, 3L, 1, "name10#123"));
        PlayerCharacter char5 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc1.getId(), Region.EU, 4L, 1, "name20#123"));

        List<Tuple4<Account, PlayerCharacter, Boolean, Integer>> updatedAccsAndChars = List.of
        (
            Tuples.of
            (
                new Account(null, Partition.GLOBAL, "tag3#123"),
                new PlayerCharacter(null, acc1.getId(), Region.EU, 1L, 1, "name2#123"),
                true,
                0
            ),
            Tuples.of
            (
                new Account(null, Partition.GLOBAL, "tag3#123"),
                new PlayerCharacter(null, acc1.getId(), Region.EU, 1L, 1, "name2#123"),
                true,
                0
            ),
            Tuples.of
            (
                new Account(null, Partition.GLOBAL, "tag4#123"),
                new PlayerCharacter(null, acc2.getId(), Region.EU, 2L, 1, "name4#123"),
                true,
                0
            ),
            Tuples.of
            (
                new Account(null, Partition.GLOBAL, "tag10#123"),
                new PlayerCharacter(null, acc1.getId(), Region.EU, 3L, 1, "name11#123"),
                false,
                0
            ),
            Tuples.of
            (
                new Account(null, Partition.GLOBAL, "tag10#123"),
                new PlayerCharacter(null, acc1.getId(), Region.EU, 4L, 1, "name20#1"),
                false,
                0
            )
        );

        OffsetDateTime char4Updated = playerCharacterDAO.getUpdated(char4.getId());
        OffsetDateTime minTimeAllowed = OffsetDateTime.now();
        assertEquals(3, playerCharacterDAO.updateAccountsAndCharacters(updatedAccsAndChars));

        //character is not updated
        assertEquals("name10#123", playerCharacterDAO.find(Region.EU, 1, 3L).orElseThrow().getName());
        OffsetDateTime char4UpdatedNew = playerCharacterDAO.getUpdated(char4.getId());
        assertTrue(char4UpdatedNew.isEqual(char4Updated));

        //char5 is updated despite being stale because it has the same name prefix
        OffsetDateTime char5Updated = playerCharacterDAO.getUpdated(char5.getId());
        assertTrue(char5Updated.isAfter(minTimeAllowed));

        playerCharacterDAO.updateUpdated(OffsetDateTime.now(), Set.of(char4.getId()));

        verifyUpdatedCharacters(minTimeAllowed);
        OffsetDateTime minAccTime =
            template.query("SELECT MIN(updated) FROM account", DAOUtils.OFFSET_DATE_TIME_RESULT_SET_EXTRACTOR);
        assertTrue(minAccTime.isAfter(minTimeAllowed));

        assertEquals("tag3#123", accountDAO.findByIds(Set.of(acc1.getId())).get(0).getBattleTag());
        assertEquals("tag4#123", accountDAO.findByIds(Set.of(acc2.getId())).get(0).getBattleTag());
        //character is rebound to another account
        assertEquals(acc3.getId(), playerCharacterDAO.find(Region.EU, 1, 3L).orElseThrow().getAccountId());

        accountDAO.updateUpdated
        (
            OffsetDateTime.now().minus(BlizzardPrivacyService.DATA_TTL.plusDays(2)),
            Set.of(acc1.getId())
        );
        accountDAO.anonymizeExpiredAccounts(OffsetDateTime.now().minusSeconds(BlizzardPrivacyService.DATA_TTL.toSeconds()).minusDays(1));
        //the account is excluded due to "from" param
        assertEquals("tag3#123", accountDAO.findByIds(Set.of(acc1.getId())).get(0).getBattleTag());

        accountDAO.anonymizeExpiredAccounts(OffsetDateTime.MIN);
        assertEquals(BasePlayerCharacter.DEFAULT_FAKE_NAME + "#211", accountDAO.findByIds(Set.of(acc1.getId())).get(0).getBattleTag());
    }

    @Test
    public void testCharacterRebinding()
    {
        //stub
        stubCharacterChain(1, true);
        stubCharacterChain(2, true);
        stubCharacterChain(3, true);
        stubCharacterChain(4, false);
        stubReport(1, 2, true);
        stubReport(3, 4, false);
        accountFollowingDAO.create(new AccountFollowing(1L, 1L));
        accountFollowingDAO.create(new AccountFollowing(2L, 2L));
        accountFollowingDAO.create(new AccountFollowing(3L, 3L));
        accountRoleDAO.addRoles(1, EnumSet.of(SC2PulseAuthority.ADMIN));
        accountRoleDAO.addRoles(2, EnumSet.of(SC2PulseAuthority.ADMIN, SC2PulseAuthority.MODERATOR));

        //rebind character2 to account 1
        playerCharacterDAO.merge(new PlayerCharacter(null, 1L, Region.EU, 2L, 2, "name2"));

        //rebound value already exists, do nothing, it will be cleared later when account expires
        List<ProPlayerAccount> proPlayerAccounts = proPlayerAccountDAO.findByProPlayerId(2);
        assertEquals(1, proPlayerAccounts.size());
        assertEquals(2, proPlayerAccounts.get(0).getAccountId());

        List<AccountFollowing> followings = accountFollowingDAO.findAccountFollowingList(2L);
        assertEquals(1, followings.size());
        assertEquals(2L, followings.get(0).getAccountId());
        assertEquals(2L, followings.get(0).getFollowingAccountId());

        evidenceVoteDAO.findByEvidenceIds(Set.of(1)).stream()
            .filter(v->v.getVoterAccountId() == 2)
            .findAny()
            .orElseThrow();

        DiscordUser discordUser2 = discordUserDAO.findByAccountId(2L, false).orElseThrow();
        assertEquals(Snowflake.of(2L), discordUser2.getId());

        //admin is untouched because it already exists, moderator is rebound
        List<SC2PulseAuthority> roles = accountRoleDAO.getRoles(1);
        assertEquals(3, roles.size());
        assertTrue(roles.contains(SC2PulseAuthority.ADMIN));
        assertTrue(roles.contains(SC2PulseAuthority.MODERATOR));

        List<SC2PulseAuthority> roles2 = accountRoleDAO.getRoles(2);
        assertEquals(2, roles2.size());
        assertTrue(roles2.contains(SC2PulseAuthority.ADMIN));

        //rebind character 3 to account 4
        playerCharacterDAO.merge(new PlayerCharacter(null, 4L, Region.EU, 3L, 3, "name3"));

        //rebound value does not exist, successfully rebound
        List<ProPlayerAccount> proPlayerAccountsRebound = proPlayerAccountDAO.findByProPlayerId(3);
        assertEquals(1, proPlayerAccountsRebound.size());
        assertEquals(4, proPlayerAccountsRebound.get(0).getAccountId());

        //old followings were moved to the new account
        List<AccountFollowing> followingsRebound = accountFollowingDAO.findAccountFollowingList(3L);
        assertTrue(followingsRebound.isEmpty());

        List<AccountFollowing> followingsRebound2 = accountFollowingDAO.findAccountFollowingList(4L);
        assertEquals(1, followingsRebound2.size());
        assertEquals(4L, followingsRebound2.get(0).getAccountId());
        assertEquals(4L, followingsRebound2.get(0).getFollowingAccountId());

        assertEquals(4, evidenceDAO.findById(false, 3).orElseThrow().getReporterAccountId());

        List<EvidenceVote> reboundVotes = evidenceVoteDAO.findByEvidenceIds(Set.of(3));
        assertEquals(1, reboundVotes.size());
        assertEquals(4L, reboundVotes.get(0).getVoterAccountId());

        assertFalse(discordUserDAO.findByAccountId(3L, false).isPresent());
        DiscordUser discordUser3 = discordUserDAO.findByAccountId(4L, false).orElseThrow();
        assertEquals(Snowflake.of(3L), discordUser3.getId());
    }

    private Account stubCharacterChain(int num, boolean bind)
    {
        DiscordUser discordUser = discordUserDAO
            .merge(Set.of(new DiscordUser(Snowflake.of(num), "name" + num, num)))
            .iterator().next();
        ProPlayer proPlayer = proPlayerDAO
            .merge(new ProPlayer(null, (long) num, "proTag" + num, "proName" + num));
        Account acc1 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag" + num));
        PlayerCharacter char1 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc1.getId(), Region.EU, (long) num, num, "name" + num));
        if(bind)
        {
            proPlayerAccountDAO.link(proPlayer.getId(), "tag" + num);
            accountDiscordUserDAO
                .create(Set.of(new AccountDiscordUser(acc1.getId(), discordUser.getId())));
        }
        return acc1;
    }

    private void stubReport(long accountId, long accountId2, boolean secondVote)
    {
        PlayerCharacterReport report = playerCharacterReportDAO.merge
        (
            new PlayerCharacterReport
            (
                null,
                accountId,
                null,
                PlayerCharacterReport.PlayerCharacterReportType.CHEATER,
                false,
                true,
                OffsetDateTime.now()
            )
        );
        Evidence evidence = evidenceDAO.create
        (
            new Evidence
            (
                null,
                report.getId(),
                accountId,
                null,
                "description",
                false,
                OffsetDateTime.now(), OffsetDateTime.now()
            )
        );
        evidenceVoteDAO.merge
        (
            new EvidenceVote
            (
                evidence.getId(),
                OffsetDateTime.now(),
                accountId,
                true,
                OffsetDateTime.now()
            )
        );
        if(secondVote) evidenceVoteDAO.merge
        (
            new EvidenceVote
            (
                evidence.getId(),
                OffsetDateTime.now(),
                accountId2,
                true,
                OffsetDateTime.now()
            )
        );
        Evidence evidence2 = evidenceDAO.create
        (
            new Evidence
            (
                null,
                report.getId(),
                accountId2,
                null,
                "description",
                false,
                OffsetDateTime.now(), OffsetDateTime.now()
            )
        );
    }

    @Test
    public void testUpdateUpdated()
    {
        Account account = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        PlayerCharacter char1 = playerCharacterDAO
            .merge(new PlayerCharacter(null, account.getId(), Region.EU, 1L, 1, "name#1"));
        OffsetDateTime newUpdated = OffsetDateTime.now().plusDays(1);
        playerCharacterDAO.updateUpdated(newUpdated, Set.of(char1.getId()));
        assertTrue(playerCharacterDAO.getUpdated(char1.getId()).isEqual(newUpdated));
    }

    @Test
    public void whenAnonymousFlagIsTrue_thenDontUpdateEntity()
    {
        //stub
        Account acc = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        PlayerCharacter pc = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc.getId(), Region.EU, 1L, 1, "name#1"));
        playerCharacterDAO.updateAnonymousFlag(pc.getId(), true);
        OffsetDateTime beforeUpdate = OffsetDateTime.now();

        //modifying operations that should update the entity
        PlayerCharacter newPlayerCharacter =
            new PlayerCharacter(null, acc.getId(), Region.EU, 1L, 1, "name#2");
        playerCharacterDAO.merge(newPlayerCharacter);
        playerCharacterDAO.updateCharacters(Set.of(newPlayerCharacter));
        playerCharacterDAO
            .updateAccountsAndCharacters(List.of(Tuples.of(acc, newPlayerCharacter, true, 1)));

        //entity wasn't updated due to anonymous flag
        PlayerCharacter foundCharacter = playerCharacterDAO.find(Set.of(pc.getId())).get(0);
        assertEquals(pc, foundCharacter);
        assertEquals("name#1", foundCharacter.getName());
        OffsetDateTime afterUpdate = template
            .queryForObject("SELECT updated FROM player_character", OffsetDateTime.class);
        assertTrue(beforeUpdate.isAfter(afterUpdate));
    }

    @Test
    public void testAnonymousFlagSetterAndGetter()
    {
        Account acc = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        PlayerCharacter pc = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc.getId(), Region.EU, 1L, 1, "name#1"));
        //false by default
        assertFalse(playerCharacterDAO.getAnonymousFlag(pc.getId()));

        playerCharacterDAO.updateAnonymousFlag(pc.getId(), true);
        assertTrue(playerCharacterDAO.getAnonymousFlag(pc.getId()));

        playerCharacterDAO.updateAnonymousFlag(pc.getId(), false);
        assertFalse(playerCharacterDAO.getAnonymousFlag(pc.getId()));
    }

    @Test
    public void testFindByUpdatedMax()
    {
        Account acc = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        PlayerCharacter pc1 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc.getId(), Region.EU, 1L, 1, "name#1"));
        PlayerCharacter pc2 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc.getId(), Region.EU, 2L, 1, "name#2"));
        PlayerCharacter pc3 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc.getId(), Region.US, 3L, 1, "name#3"));
        PlayerCharacter pc4 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc.getId(), Region.US, 4L, 1, "name#4"));

        assertEquals(4, playerCharacterDAO.countByUpdatedMax(OffsetDateTime.now(), Set.of()));
        assertEquals(2, playerCharacterDAO
            .countByUpdatedMax(OffsetDateTime.now(), Set.of(Region.EU)));
        assertEquals(1, playerCharacterDAO
            .updateUpdated(OffsetDateTime.now().plusDays(1), Set.of(pc1.getId())));
        assertEquals(3, playerCharacterDAO.countByUpdatedMax(OffsetDateTime.now(), Set.of()));
        assertEquals(1, playerCharacterDAO
            .countByUpdatedMax(OffsetDateTime.now(), Set.of(Region.EU)));
    }

    @Test
    public void testFindByAccountId()
    {
        Account acc = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        Account acc2 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#2"));
        PlayerCharacter pc1 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc.getId(), Region.EU, 1L, 1, "name#1"));
        PlayerCharacter pc2 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc.getId(), Region.EU, 2L, 1, "name#2"));
        PlayerCharacter pc3 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc2.getId(), Region.US, 3L, 1, "name#3"));

        List<PlayerCharacter> characters = playerCharacterDAO.findByAccountId(acc.getId());
        assertEquals(2, characters.size());
        characters.sort(Comparator.comparing(PlayerCharacter::getId));
        assertEquals(pc1, characters.get(0));
        assertEquals(pc2, characters.get(1));
    }

    @Test
    public void testFindIdsByAccountIds()
    {
        Account acc1 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        Account acc2 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#2"));
        Account acc3 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#3"));
        PlayerCharacter pc1 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc1.getId(), Region.EU, 1L, 1, "name#1"));
        PlayerCharacter pc2 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc1.getId(), Region.EU, 2L, 1, "name#2"));
        PlayerCharacter pc3 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc2.getId(), Region.US, 3L, 1, "name#3"));
        PlayerCharacter pc4 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc3.getId(), Region.US, 4L, 1, "name#4"));

        List<Long> foundIds = playerCharacterDAO
            .findIdsByAccountIds(Set.of(acc1.getId(), acc3.getId()));
        foundIds.sort(Comparator.naturalOrder());
        Assertions.assertThat(foundIds).isEqualTo(List.of(
            pc1.getId(),
            pc2.getId(),
            pc4.getId()
        ));
    }

}
