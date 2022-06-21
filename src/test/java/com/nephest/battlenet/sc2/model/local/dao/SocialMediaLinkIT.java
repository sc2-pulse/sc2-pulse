// Copyright (C) 2020-2022 Oleksandr Masniuk
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

    @Test
    public void testFindByTypes()
    {
        ProPlayer proPlayer1 = proPlayerDAO
            .merge(new ProPlayer(null, new byte[]{0x1}, "nick", "name"));
        ProPlayer proPlayer2 = proPlayerDAO
            .merge(new ProPlayer(null, new byte[]{0x2}, "nick2", "name2"));
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
    public void testProtection()
    {
        ProPlayer proPlayer1 = proPlayerDAO
            .merge(new ProPlayer(null, new byte[]{0x1}, "nick", "name"));
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
