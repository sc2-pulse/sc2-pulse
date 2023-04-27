// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.SocialMediaLink;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
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
public class SocialMediaLinkIT
{

    @Autowired
    private ProPlayerDAO proPlayerDAO;

    @Autowired
    private SocialMediaLinkDAO socialMediaLinkDAO;

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

    private static void verifyLink
    (
        SocialMediaLink link,
        Long proPlayerId,
        SocialMedia type,
        String url,
        boolean isProtected
    )
    {
        assertEquals(proPlayerId, link.getProPlayerId());
        assertEquals(type, link.getType());
        assertEquals(url, link.getUrl());
        assertEquals(isProtected, link.isProtected());
    }

    @Test
    public void testFindByProPlayerId()
    {
        ProPlayer proPlayer1 = proPlayerDAO
            .merge(new ProPlayer(null, 1L, "nick", "name"));
        ProPlayer proPlayer2 = proPlayerDAO
            .merge(new ProPlayer(null, 2L, "nick2", "name2"));
        socialMediaLinkDAO.merge
        (
            new SocialMediaLink(proPlayer1.getId(), SocialMedia.TWITCH, "url1"),
            new SocialMediaLink(proPlayer1.getId(), SocialMedia.ALIGULAC, "url2"),
            new SocialMediaLink(proPlayer2.getId(), SocialMedia.TWITCH, "url3")
        );

        List<SocialMediaLink> links = socialMediaLinkDAO.find(proPlayer1.getId());
        assertEquals(2, links.size());
        verifyLink(links.get(0), proPlayer1.getId(), SocialMedia.ALIGULAC, "url2", false);
        verifyLink(links.get(1), proPlayer1.getId(), SocialMedia.TWITCH, "url1", false);
    }

    @Test
    public void testFindByTypes()
    {
        ProPlayer proPlayer1 = proPlayerDAO
            .merge(new ProPlayer(null, 1L, "nick", "name"));
        ProPlayer proPlayer2 = proPlayerDAO
            .merge(new ProPlayer(null, 2L, "nick2", "name2"));
        socialMediaLinkDAO.merge
        (
            new SocialMediaLink(proPlayer1.getId(), SocialMedia.TWITCH, "url1"),
            new SocialMediaLink(proPlayer2.getId(), SocialMedia.TWITCH, "url2"),
            new SocialMediaLink(proPlayer1.getId(), SocialMedia.ALIGULAC, "url3")
        );

        List<SocialMediaLink> links = socialMediaLinkDAO.findByTypes(SocialMedia.TWITCH);
        assertEquals(2, links.size());
        links.forEach(l->assertEquals(SocialMedia.TWITCH, l.getType()));
    }

    @Test
    public void testFindByIdCursor()
    {
        ProPlayer proPlayer1 = proPlayerDAO
            .merge(new ProPlayer(null, 1L, "nick", "name"));
        ProPlayer proPlayer2 = proPlayerDAO
            .merge(new ProPlayer(null, 2L, "nick2", "name2"));
        ProPlayer proPlayer3 = proPlayerDAO
            .merge(new ProPlayer(null, 3L, "nick3", "name3"));
        ProPlayer proPlayer4 = proPlayerDAO
            .merge(new ProPlayer(null, 4L, "nick4", "name4"));

        socialMediaLinkDAO.merge
        (
            new SocialMediaLink(proPlayer1.getId(), SocialMedia.TWITCH, "url1"),
            new SocialMediaLink(proPlayer1.getId(), SocialMedia.ALIGULAC, "url2"),
            new SocialMediaLink(proPlayer2.getId(), SocialMedia.TWITCH, "url3"),
            new SocialMediaLink(proPlayer3.getId(), SocialMedia.TWITCH, "url4"),
            new SocialMediaLink(proPlayer3.getId(), SocialMedia.ALIGULAC, "url5"),
            new SocialMediaLink(proPlayer4.getId(), SocialMedia.TWITCH, "url6"),
            new SocialMediaLink(proPlayer4.getId(), SocialMedia.ALIGULAC, "url7")
        );

        List<SocialMediaLink> links1 = socialMediaLinkDAO
            .findByIdCursor(null, SocialMedia.ALIGULAC, 2);
        assertEquals(2, links1.size());
        assertEquals("url2", links1.get(0).getUrl());
        assertEquals("url5", links1.get(1).getUrl());

        List<SocialMediaLink> links2 = socialMediaLinkDAO
            .findByIdCursor(links1.get(1).getProPlayerId(), SocialMedia.ALIGULAC, 2);
        assertEquals(1, links2.size());
        assertEquals("url7", links2.get(0).getUrl());

        List<SocialMediaLink> links3 = socialMediaLinkDAO
            .findByIdCursor(links2.get(0).getProPlayerId(), SocialMedia.ALIGULAC, 2);
        assertTrue(links3.isEmpty());
    }

    @Test
    public void testProtection()
    {
        ProPlayer proPlayer1 = proPlayerDAO
            .merge(new ProPlayer(null, 1L, "nick", "name"));
        socialMediaLinkDAO.merge
        (
            true,
            new SocialMediaLink
            (
                proPlayer1.getId(),
                SocialMedia.TWITCH,
                "url1",
                OffsetDateTime.now(),
                true
            )
        );

        socialMediaLinkDAO.merge
        (
            true,
            new SocialMediaLink
            (
                proPlayer1.getId(),
                SocialMedia.TWITCH,
                "url2",
                OffsetDateTime.now(),
                false
            )
        );
        //protected links are not updated
        SocialMediaLink link = socialMediaLinkDAO.findByTypes(SocialMedia.TWITCH).get(0);
        assertEquals("url1", link.getUrl());
        assertTrue(link.isProtected());

        //set isProtected flag to false
        socialMediaLinkDAO.merge
        (
            false,
            new SocialMediaLink
            (
                proPlayer1.getId(),
                SocialMedia.TWITCH,
                "url1",
                OffsetDateTime.now(),
                false
            )
        );

        socialMediaLinkDAO.merge
        (
            true,
            new SocialMediaLink
            (
                proPlayer1.getId(),
                SocialMedia.TWITCH,
                "url2",
                OffsetDateTime.now(),
                false
            )
        );
        SocialMediaLink link2 = socialMediaLinkDAO.findByTypes(SocialMedia.TWITCH).get(0);
        assertEquals("url2", link2.getUrl());
        assertFalse(link2.isProtected());
    }

}
