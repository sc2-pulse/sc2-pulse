// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.ClanMember;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
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
        Clan[] clans = clanDAO.merge
        (
            new Clan(null, "clan1", Region.EU, "name1"),
            new Clan(null, "clan2", Region.EU, "name2")
        );

        clanMemberDAO.merge
        (
            null, //nulls should be skipped
            new ClanMember(char1.getId(), clans[0].getId()),
            //duplicates should be skipped
            new ClanMember(char1.getId(), clans[0].getId()),
            new ClanMember(char2.getId(), clans[1].getId())
        );

        List<ClanMember> foundClans1 = clanMemberDAO
            .find(char1.getId(), char2.getId(), char3.getId());
        foundClans1.sort(Comparator.comparing(ClanMember::getPlayerCharacterId));
        assertEquals(2, foundClans1.size());

        ClanMember pcc1 = foundClans1.get(0);
        assertEquals(char1.getId(), pcc1.getPlayerCharacterId());
        assertEquals(clans[0].getId(), pcc1.getClanId());

        ClanMember pcc2 = foundClans1.get(1);
        assertEquals(char2.getId(), pcc2.getPlayerCharacterId());
        assertEquals(clans[1].getId(), pcc2.getClanId());

        //2nd merge
        clanMemberDAO.merge
        (
            new ClanMember(char2.getId(), clans[0].getId()), //updated
            new ClanMember(char3.getId(), clans[1].getId()) //new
        );

        List<ClanMember> foundClans2 = clanMemberDAO
            .find(char1.getId(), char2.getId(), char3.getId());
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
        Clan clan = clanDAO.merge(new Clan(null, "clan1", Region.EU, "name1"))[0];

        clanMemberDAO.merge
        (
            new ClanMember(char1.getId(), clan.getId()),
            new ClanMember(char2.getId(), clan.getId()),
            new ClanMember(char3.getId(), clan.getId())
        );

        assertEquals(3, clanMemberDAO.find(1L, 2L, 3L).size());

        assertEquals(2, clanMemberDAO.remove(2L, 3L));
        List<ClanMember> foundMembers = clanMemberDAO.find(1L, 2L, 3L);
        assertEquals(1, foundMembers.size());

        ClanMember pcc1 = foundMembers.get(0);
        assertEquals(char1.getId(), pcc1.getPlayerCharacterId());
        assertEquals(clan.getId(), pcc1.getClanId());
    }

}
