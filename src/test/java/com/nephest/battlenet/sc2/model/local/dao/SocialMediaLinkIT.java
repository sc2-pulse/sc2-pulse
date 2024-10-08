// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.SocialMediaLink;
import com.nephest.battlenet.sc2.model.local.SocialMediaUserId;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
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
        ProPlayer proPlayer3 = proPlayerDAO
            .merge(new ProPlayer(null, 3L, "nick3", "name3"));
        socialMediaLinkDAO.merge(Set.of(
            new SocialMediaLink(proPlayer1.getId(), SocialMedia.TWITCH, "url1"),
            new SocialMediaLink(proPlayer1.getId(), SocialMedia.ALIGULAC, "url2"),
            new SocialMediaLink(proPlayer2.getId(), SocialMedia.TWITCH, "url3"),
            new SocialMediaLink(proPlayer3.getId(), SocialMedia.TWITCH, "url4")
        ));

        List<SocialMediaLink> links = socialMediaLinkDAO
            .find(Set.of(proPlayer1.getId(), proPlayer2.getId()));
        assertEquals(3, links.size());
        verifyLink(links.get(0), proPlayer1.getId(), SocialMedia.ALIGULAC, "url2", false);
        verifyLink(links.get(1), proPlayer1.getId(), SocialMedia.TWITCH, "url1", false);
        verifyLink(links.get(2), proPlayer2.getId(), SocialMedia.TWITCH, "url3", false);
    }

    @Test
    public void testFindByTypes()
    {
        ProPlayer proPlayer1 = proPlayerDAO
            .merge(new ProPlayer(null, 1L, "nick", "name"));
        ProPlayer proPlayer2 = proPlayerDAO
            .merge(new ProPlayer(null, 2L, "nick2", "name2"));
        socialMediaLinkDAO.merge(Set.of(
            new SocialMediaLink(proPlayer1.getId(), SocialMedia.TWITCH, "url1"),
            new SocialMediaLink(proPlayer2.getId(), SocialMedia.TWITCH, "url2"),
            new SocialMediaLink(proPlayer1.getId(), SocialMedia.ALIGULAC, "url3")
        ));

        List<SocialMediaLink> links = socialMediaLinkDAO.findByTypes(EnumSet.of(SocialMedia.TWITCH));
        assertEquals(2, links.size());
        links.forEach(l->assertEquals(SocialMedia.TWITCH, l.getType()));
    }

    @Test
    public void testFindByServiceUserId()
    {
        ProPlayer proPlayer1 = proPlayerDAO
            .merge(new ProPlayer(null, 1L, "nick", "name"));
        ProPlayer proPlayer2 = proPlayerDAO
            .merge(new ProPlayer(null, 2L, "nick2", "name2"));
        ProPlayer proPlayer3 = proPlayerDAO
            .merge(new ProPlayer(null, 3L, "nick3", "name3"));
        OffsetDateTime odt = SC2Pulse.offsetDateTime();
        SocialMediaLink[] links = socialMediaLinkDAO.merge(new LinkedHashSet<>(List.of(
            new SocialMediaLink(proPlayer1.getId(), SocialMedia.ALIGULAC, "url1", odt, "1", false),
            new SocialMediaLink(proPlayer1.getId(), SocialMedia.TWITCH, "url2", odt, "1", false),

            new SocialMediaLink(proPlayer2.getId(), SocialMedia.ALIGULAC, "url3", odt, "2", false),
            new SocialMediaLink(proPlayer2.getId(), SocialMedia.TWITCH, "url4", odt, "2", false),

            new SocialMediaLink(proPlayer3.getId(), SocialMedia.TWITCH, "url5", odt, "1", false)
        )))
            .toArray(SocialMediaLink[]::new);

        List<SocialMediaLink> foundLinks = socialMediaLinkDAO.findByServiceUserIds(Set.of(
            new SocialMediaUserId(SocialMedia.TWITCH, "1"),
            new SocialMediaUserId(SocialMedia.ALIGULAC, "2")
        ));
        assertEquals(3, foundLinks.size());
        foundLinks.sort(SocialMediaLink.NATURAL_ID_COMPARATOR);
        Assertions.assertThat(foundLinks)
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .isEqualTo(List.of(links[1], links[2], links[4]));
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

        OffsetDateTime odt1 = SC2Pulse.offsetDateTime();
        SocialMediaLink[] links = new SocialMediaLink[]
        {
            new SocialMediaLink(proPlayer1.getId(), SocialMedia.TWITCH, "url1"),
            new SocialMediaLink(proPlayer1.getId(), SocialMedia.ALIGULAC, "url2", odt1, "11", false),
            new SocialMediaLink(proPlayer2.getId(), SocialMedia.TWITCH, "url3"),
            new SocialMediaLink(proPlayer3.getId(), SocialMedia.TWITCH, "url4"),
            new SocialMediaLink(proPlayer3.getId(), SocialMedia.ALIGULAC, "url5", odt1, "55", false),
            new SocialMediaLink(proPlayer4.getId(), SocialMedia.TWITCH, "url6"),
            new SocialMediaLink(proPlayer4.getId(), SocialMedia.ALIGULAC, "url7")
        };
        socialMediaLinkDAO.merge(Set.copyOf(List.of(links)));

        List<SocialMediaLink> links1 = socialMediaLinkDAO
            .findByIdCursor(null, SocialMedia.ALIGULAC, 2);
        assertEquals(2, links1.size());
        links1.sort(SocialMediaLink.NATURAL_ID_COMPARATOR);
        Assertions.assertThat(links1.get(0))
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .isEqualTo(links[1]);
        Assertions.assertThat(links1.get(1))
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .isEqualTo(links[4]);

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
        assertEquals(1,  proPlayerDAO.findAll().get(0).getVersion());
        socialMediaLinkDAO.merge
        (
            true,
            Set.of(new SocialMediaLink
            (
                proPlayer1.getId(),
                SocialMedia.TWITCH,
                "url1",
                SC2Pulse.offsetDateTime(),
                "123",
                true
            ))
        );
        //pro player version was updated because link was updated
        assertEquals(2,  proPlayerDAO.findAll().get(0).getVersion());

        socialMediaLinkDAO.merge
        (
            true,
            Set.of(new SocialMediaLink
            (
                proPlayer1.getId(),
                SocialMedia.TWITCH,
                "url2",
                SC2Pulse.offsetDateTime(),
                "123",
                false
            ))
        );
        //protected links are not updated
        SocialMediaLink link = socialMediaLinkDAO
            .findByTypes(EnumSet.of(SocialMedia.TWITCH)).get(0);
        assertEquals("url1", link.getUrl());
        assertTrue(link.isProtected());
        assertEquals(2,  proPlayerDAO.findAll().get(0).getVersion());

        //set isProtected flag to false
        socialMediaLinkDAO.merge
        (
            false,
            Set.of(new SocialMediaLink
            (
                proPlayer1.getId(),
                SocialMedia.TWITCH,
                "url1",
                SC2Pulse.offsetDateTime(),
                "123",
                false
            ))
        );
        assertEquals(3,  proPlayerDAO.findAll().get(0).getVersion());

        socialMediaLinkDAO.merge
        (
            true,
            Set.of(new SocialMediaLink
            (
                proPlayer1.getId(),
                SocialMedia.TWITCH,
                "url2",
                SC2Pulse.offsetDateTime(),
                "123",
                false
            ))
        );
        SocialMediaLink link2 = socialMediaLinkDAO
            .findByTypes(EnumSet.of(SocialMedia.TWITCH)).get(0);
        assertEquals("url2", link2.getUrl());
        assertFalse(link2.isProtected());
        assertEquals(4,  proPlayerDAO.findAll().get(0).getVersion());
    }

    @Test
    public void whenNoChanges_thenDontUpdate()
    {
        ProPlayer proPlayer1 = proPlayerDAO
            .merge(new ProPlayer(null, 1L, "nick", "name"));
        assertEquals(1,  proPlayerDAO.findAll().get(0).getVersion());
        OffsetDateTime odt1 = SC2Pulse.offsetDateTime();
        socialMediaLinkDAO.merge
        (
            true,
            Set.of(new SocialMediaLink
            (
                proPlayer1.getId(),
                SocialMedia.TWITCH,
                "url1",
                odt1,
                "123",
                true
            ))
        );
        assertEquals(2,  proPlayerDAO.findAll().get(0).getVersion());

        //no changes
        socialMediaLinkDAO.merge
        (
            true,
            Set.of(new SocialMediaLink
            (
                proPlayer1.getId(),
                SocialMedia.TWITCH,
                "url1",
                odt1.plusSeconds(10),
                "123",
                true
            ))
        );
        SocialMediaLink link = socialMediaLinkDAO
            .findByTypes(EnumSet.of(SocialMedia.TWITCH)).get(0);
        assertTrue(link.getUpdated().isEqual(odt1));
        assertEquals(2,  proPlayerDAO.findAll().get(0).getVersion());
    }

    @Test
    public void whenRemove_thenRemoveAndUpdateProPlayerVersion()
    {
        ProPlayer proPlayer1 = proPlayerDAO
            .merge(new ProPlayer(null, 1L, "nick", "name"));
        ProPlayer proPlayer2 = proPlayerDAO
            .merge(new ProPlayer(null, 2L, "nick2", "name2"));
        assertEquals(1, proPlayerDAO.find(Set.of(proPlayer1.getId())).get(0).getVersion());
        OffsetDateTime odt1 = SC2Pulse.offsetDateTime();
        socialMediaLinkDAO.merge
        (
            true,
            Set.of
            (
                new SocialMediaLink
                (
                    proPlayer1.getId(),
                    SocialMedia.TWITCH,
                    "url1",
                    odt1,
                    null,
                    true
                ),
                new SocialMediaLink
                (
                    proPlayer1.getId(),
                    SocialMedia.YOUTUBE,
                    "url1",
                    odt1,
                    "22",
                    true
                ),
                new SocialMediaLink
                (
                    proPlayer1.getId(),
                    SocialMedia.ALIGULAC,
                    "url1",
                    odt1,
                    "33",
                    true
                )
            )
        );
        assertEquals(4, proPlayerDAO.find(Set.of(proPlayer1.getId())).get(0).getVersion());
        socialMediaLinkDAO.remove(Set.of(
            new SocialMediaLink(proPlayer1.getId(), SocialMedia.TWITCH, null),
            new SocialMediaLink(proPlayer1.getId(), SocialMedia.ALIGULAC, null)
        ));
        List<SocialMediaLink> links = socialMediaLinkDAO.find(Set.of(proPlayer1.getId()));
        assertEquals(1, links.size());
        assertEquals(SocialMedia.YOUTUBE, links.get(0).getType());
        assertEquals(6,  proPlayerDAO.findAll().get(0).getVersion());
    }

}
