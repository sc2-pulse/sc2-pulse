// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.twitch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerDAO;
import com.nephest.battlenet.sc2.model.local.dao.SocialMediaLinkDAO;
import com.nephest.battlenet.sc2.model.twitch.dao.TwitchUserDAO;
import com.nephest.battlenet.sc2.model.twitch.dao.TwitchVideoDAO;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
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
public class TwitchIT
{

    @Autowired
    private TwitchUserDAO twitchUserDAO;

    @Autowired
    private TwitchVideoDAO twitchVideoDAO;

    @Autowired
    private ProPlayerDAO proPlayerDAO;

    @Autowired
    private SocialMediaLinkDAO socialMediaLinkDAO;

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
    public void testChain()
    {
        TwitchUser user1 = new TwitchUser(1L, "login1");
        TwitchUser user2 = new TwitchUser(2L, "login2");
        TwitchUser user3 = new TwitchUser(3L, "login3");

        //new users
        twitchUserDAO.merge(user1, user2);
        List<TwitchUser> users1 = twitchUserDAO.findById(1L, 2L);
        users1.sort(Comparator.comparing(TwitchUser::getId));
        assertEquals(2, users1.size());

        TwitchUser foundUser1 = users1.get(0);
        assertEquals(1L, foundUser1.getId());
        assertEquals("login1", foundUser1.getLogin());

        assertTrue(users1.contains(user2));

        //duplicate, already existing user, new
        twitchUserDAO.merge(user2, user2, user3);

        List<TwitchUser> users2 = twitchUserDAO.findById(1L, 2L, 3L);
        assertEquals(3, users2.size());
        assertTrue(users2.contains(user1));
        assertTrue(users2.contains(user2));
        assertTrue(users2.contains(user3));

        OffsetDateTime begin = OffsetDateTime.now();
        OffsetDateTime end = begin.plusMinutes(1);
        TwitchVideo video1 = new TwitchVideo(1L, 1L, "url1", begin, end);
        TwitchVideo video2 = new TwitchVideo(2L, 2L, "url2", begin, end);
        TwitchVideo video3 = new TwitchVideo(3L, 3L, "url3", begin, end);

        //new videos
        twitchVideoDAO.merge(video1, video2);
        List<TwitchVideo> videos1 = twitchVideoDAO.findById(1L, 2L);
        videos1.sort(Comparator.comparing(TwitchVideo::getId));
        assertEquals(2, videos1.size());
        TwitchVideo foundVideo1 = videos1.get(0);
        assertEquals(1L, foundVideo1.getId());
        assertEquals(1L, foundVideo1.getTwitchUserId());
        assertEquals("url1", foundVideo1.getUrl());
        assertTrue(begin.isEqual(foundVideo1.getBegin()));
        assertTrue(end.isEqual(foundVideo1.getEnd()));

        assertTrue(videos1.contains(video2));

        //duplicate, already existing, new
        twitchVideoDAO.merge(video2, video2, video3);
        List<TwitchVideo> videos2 = twitchVideoDAO.findById(1L, 2L, 3L);
        assertEquals(3, videos2.size());
        assertTrue(videos2.contains(video1));
        assertTrue(videos2.contains(video2));
        assertTrue(videos2.contains(video3));

        //updated video
        OffsetDateTime newBegin = begin.plusMinutes(1);
        OffsetDateTime newEnd = end.plusMinutes(1);
        TwitchVideo video4 = new TwitchVideo(1L, 1L, "url11", newBegin, newEnd);
        twitchVideoDAO.merge(video4);
        TwitchVideo updatedVideo = twitchVideoDAO.findById(1L).get(0);
        assertEquals("url11", updatedVideo.getUrl());
        assertTrue(newBegin.isEqual(updatedVideo.getBegin()));
        assertTrue(newEnd.isEqual(updatedVideo.getEnd()));
    }

}
