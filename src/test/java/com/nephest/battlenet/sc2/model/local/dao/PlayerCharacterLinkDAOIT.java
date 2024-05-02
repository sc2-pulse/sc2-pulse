// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterLink;
import jakarta.validation.ConstraintViolationException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
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
public class PlayerCharacterLinkDAOIT
{

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private PlayerCharacterDAO playerCharacterDAO;

    @Autowired
    private PlayerCharacterLinkDAO playerCharacterLinkDAO;

    private Account account;
    private PlayerCharacter character;

    @BeforeEach
    public void beforeEach(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        account = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        character = playerCharacterDAO.merge(
            new PlayerCharacter(null, account.getId(), Region.EU, 1L, 1, "name#1"));
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
    public void whenInvalidLink_thenThrowException()
    {
        assertThrows
        (
            ConstraintViolationException.class,
            ()->playerCharacterLinkDAO.merge(Set.of(
                new PlayerCharacterLink(character.getId(), SocialMedia.ALIGULAC, "")
            ))
        );
    }

    @Test
    public void testMerge()
    {
        //generate additional data
        PlayerCharacter character2 = playerCharacterDAO.merge(
            new PlayerCharacter(null, account.getId(), Region.US, 1L, 1, "name#1"));

        playerCharacterLinkDAO.merge(Set.of(
            new PlayerCharacterLink(character.getId(), SocialMedia.ALIGULAC, "url11"),
            new PlayerCharacterLink(character.getId(), SocialMedia.LIQUIPEDIA, "url12"),

            new PlayerCharacterLink(character2.getId(), SocialMedia.ALIGULAC, "url21")
        ));

        List<PlayerCharacterLink> links1 = playerCharacterLinkDAO.find(character.getId());
        assertEquals(2, links1.size());
        links1.sort(PlayerCharacterLink.NATURAL_ID_COMPARATOR);
        verifyLink(links1.get(0), character.getId(), SocialMedia.ALIGULAC, "url11");
        verifyLink(links1.get(1), character.getId(), SocialMedia.LIQUIPEDIA, "url12");

        playerCharacterLinkDAO.merge(Set.of(
            //updated
            new PlayerCharacterLink(character.getId(), SocialMedia.ALIGULAC, "url13"),
            //inserted
            new PlayerCharacterLink(character.getId(), SocialMedia.YOUTUBE, "url14")
        ));

        List<PlayerCharacterLink> links2 = playerCharacterLinkDAO.find(character.getId());
        assertEquals(3, links2.size());
        links2.sort(PlayerCharacterLink.NATURAL_ID_COMPARATOR);
        //url is updated
        verifyLink(links2.get(0), character.getId(), SocialMedia.ALIGULAC, "url13");
        //no changes
        verifyLink(links2.get(1), character.getId(), SocialMedia.LIQUIPEDIA, "url12");
        //new link is inserted
        verifyLink(links2.get(2), character.getId(), SocialMedia.YOUTUBE, "url14");
    }

    @Test
    public void testFindByTypeAndUrl()
    {
        PlayerCharacter character2 = playerCharacterDAO.merge(
            new PlayerCharacter(null, account.getId(), Region.US, 1L, 1, "name#1"));
        PlayerCharacter character3 = playerCharacterDAO.merge(
            new PlayerCharacter(null, account.getId(), Region.KR, 1L, 1, "name#1"));

        playerCharacterLinkDAO.merge(Set.of(
            new PlayerCharacterLink(character.getId(), SocialMedia.ALIGULAC, "url1"),
            new PlayerCharacterLink(character.getId(), SocialMedia.LIQUIPEDIA, "url1"),

            new PlayerCharacterLink(character2.getId(), SocialMedia.ALIGULAC, "url1"),

            new PlayerCharacterLink(character3.getId(), SocialMedia.ALIGULAC, "url3")
        ));

        List<PlayerCharacterLink> links = playerCharacterLinkDAO.find(SocialMedia.ALIGULAC, "url1");
        assertEquals(2, links.size());
        links.sort(PlayerCharacterLink.NATURAL_ID_COMPARATOR);

        verifyLink(links.get(0), character.getId(), SocialMedia.ALIGULAC, "url1");
        verifyLink(links.get(1), character2.getId(), SocialMedia.ALIGULAC, "url1");
    }

    private void verifyLink(PlayerCharacterLink link, Long characterId, SocialMedia type, String url)
    {
        assertEquals(characterId, link.getPlayerCharacterId());
        assertEquals(type, link.getType());
        assertEquals(url, link.getRelativeUrl());
    }

}
