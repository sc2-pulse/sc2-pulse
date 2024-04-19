// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.ClanMember;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class ClanMemberIT
{

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private PlayerCharacterDAO playerCharacterDAO;

    @Autowired
    private ClanDAO clanDAO;

    @Autowired
    private ClanMemberDAO clanMemberDAO;

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
    public void testMerge()
    {
        Account acc = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        PlayerCharacter char1 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc.getId(), Region.EU, 1L, 1, "name#1"));
        PlayerCharacter char2 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc.getId(), Region.EU, 2L, 2, "name#2"));
        PlayerCharacter char3 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc.getId(), Region.EU, 3L, 3, "name#3"));
        Clan[] clans = clanDAO.merge(new LinkedHashSet<>(List.of(
            new Clan(null, "clan1", Region.EU, "name1"),
            new Clan(null, "clan2", Region.EU, "name2")
        )))
            .toArray(Clan[]::new);

        clanMemberDAO.merge(Set.of(
            new ClanMember(char1.getId(), clans[0].getId()),
            new ClanMember(char2.getId(), clans[1].getId())
        ));

        List<ClanMember> foundClans1 = clanMemberDAO
            .find(Set.of(char1.getId(), char2.getId(), char3.getId()));
        foundClans1.sort(Comparator.comparing(ClanMember::getPlayerCharacterId));
        assertEquals(2, foundClans1.size());

        ClanMember pcc1 = foundClans1.get(0);
        assertEquals(char1.getId(), pcc1.getPlayerCharacterId());
        assertEquals(clans[0].getId(), pcc1.getClanId());

        ClanMember pcc2 = foundClans1.get(1);
        assertEquals(char2.getId(), pcc2.getPlayerCharacterId());
        assertEquals(clans[1].getId(), pcc2.getClanId());

        OffsetDateTime beforeSecondMerge = OffsetDateTime.now();
        //2nd merge
        clanMemberDAO.merge(Set.of(
            new ClanMember(char2.getId(), clans[0].getId()), //updated
            new ClanMember(char3.getId(), clans[1].getId()) //new
        ));

        List<ClanMember> foundClans2 = clanMemberDAO
            .find(Set.of(char1.getId(), char2.getId(), char3.getId()));
        foundClans2.sort(Comparator.comparing(ClanMember::getPlayerCharacterId));
        assertEquals(3, foundClans2.size());

        //no changes
        ClanMember pcc1_1 = foundClans2.get(0);
        assertEquals(char1.getId(), pcc1_1.getPlayerCharacterId());
        assertEquals(clans[0].getId(), pcc1_1.getClanId());

        //updated clan id
        ClanMember pcc2_2 = foundClans2.get(1);
        assertEquals(char2.getId(), pcc2_2.getPlayerCharacterId());
        assertEquals(clans[0].getId(), pcc2_2.getClanId());

        //new
        ClanMember pcc2_3 = foundClans2.get(2);
        assertEquals(char3.getId(), pcc2_3.getPlayerCharacterId());
        assertEquals(clans[1].getId(), pcc2_3.getClanId());

        //"updated" field was updated
        OffsetDateTime updatedOdt = template.queryForObject
        (
            "SELECT updated "
            + "FROM clan_member "
            + "WHERE player_character_id = " + char2.getId(),
            OffsetDateTime.class
        );
        assertTrue(beforeSecondMerge.isBefore(updatedOdt));

        //"updated" field was not updated
        OffsetDateTime notUpdatedOdt = template.queryForObject
        (
            "SELECT updated "
            + "FROM clan_member "
            + "WHERE player_character_id = " + char1.getId(),
            OffsetDateTime.class
        );
        assertFalse(beforeSecondMerge.isBefore(notUpdatedOdt));
    }

    @Test
    public void testRemoveByIds()
    {
        Account acc = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        PlayerCharacter char1 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc.getId(), Region.EU, 1L, 1, "name#1"));
        PlayerCharacter char2 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc.getId(), Region.EU, 2L, 2, "name#2"));
        PlayerCharacter char3 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc.getId(), Region.EU, 3L, 3, "name#3"));
        Clan clan = clanDAO.merge(Set.of(new Clan(null, "clan1", Region.EU, "name1")))
            .iterator().next();

        clanMemberDAO.merge(Set.of(
            new ClanMember(char1.getId(), clan.getId()),
            new ClanMember(char2.getId(), clan.getId()),
            new ClanMember(char3.getId(), clan.getId())
        ));

        assertEquals(3, clanMemberDAO.find(Set.of(1L, 2L, 3L)).size());

        assertEquals(2, clanMemberDAO.remove(Set.of(2L, 3L)));
        List<ClanMember> foundMembers = clanMemberDAO.find(Set.of(1L, 2L, 3L));
        assertEquals(1, foundMembers.size());

        ClanMember pcc1 = foundMembers.get(0);
        assertEquals(char1.getId(), pcc1.getPlayerCharacterId());
        assertEquals(clan.getId(), pcc1.getClanId());
    }

    @Test
    public void testRemoveExpired()
    {
        Account acc = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        PlayerCharacter char1 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc.getId(), Region.EU, 1L, 1, "name#1"));
        PlayerCharacter char2 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc.getId(), Region.EU, 2L, 2, "name#2"));
        Clan clan = clanDAO.merge(Set.of(new Clan(null, "clan1", Region.EU, "name1")))
            .iterator().next();
        clanMemberDAO.merge(Set.of(
            new ClanMember(char1.getId(), clan.getId()),
            new ClanMember(char2.getId(), clan.getId())
        ));

        template.update
        (
            "UPDATE clan_member "
            + "SET updated = NOW() - INTERVAL '" + ClanMemberDAO.TTL.toHours() +  " hours' "
            + "WHERE player_character_id = " + char1.getId()
        );

        OffsetDateTime expiredOdt = OffsetDateTime.now().minus(ClanMemberDAO.TTL);
        assertEquals(1, clanMemberDAO.getInactiveCount(expiredOdt));
        assertIterableEquals(List.of(char1.getId()), clanMemberDAO.removeExpired());
        assertTrue(clanMemberDAO.find(Set.of(char1.getId())).isEmpty());
        assertFalse(clanMemberDAO.find(Set.of(char2.getId())).isEmpty());
    }

}
