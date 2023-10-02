// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.community;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.ProPlayerAccount;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.SocialMediaLink;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerAccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerDAO;
import com.nephest.battlenet.sc2.model.local.dao.SocialMediaLinkDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderProPlayer;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.util.wrapper.ThreadLocalRandomSupplier;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

@SpringBootTest(classes = {AllTestConfig.class})
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class CommunityVideoStreamIT
{

    @Autowired
    private CommunityService communityService;

    @Autowired
    private LadderSearchDAO ladderSearchDAO;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VideoStreamSupplier videoStreamSupplier;

    @MockBean
    private ThreadLocalRandomSupplier randomSupplier;

    private static SocialMediaLink[] links;
    private static VideoStream[] streams;
    private static ProPlayer[] proPlayers;

    @BeforeEach
    public void beforeAll
    (
        @Autowired DataSource dataSource,
        @Autowired ProPlayerDAO proPlayerDAO,
        @Autowired ProPlayerAccountDAO proPlayerAccountDAO,
        @Autowired SocialMediaLinkDAO socialMediaLinkDAO,
        @Autowired SeasonGenerator seasonGenerator,
        @Autowired JdbcTemplate jdbcTemplate
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        seasonGenerator.generateDefaultSeason(0);

        Account[] accounts = seasonGenerator.generateAccounts(Partition.GLOBAL, "btag", 7);
        PlayerCharacter[] characters = seasonGenerator.generateCharacters("char", accounts, Region.EU, 1L);
        ArrayList<PlayerCharacter> teamCharacters = new ArrayList<>(Arrays.asList(characters));
        teamCharacters.add(0, characters[0]);
        List<Team> teams = seasonGenerator
            .createTeams(teamCharacters.toArray(PlayerCharacter[]::new));
        //invalid teams due to old last_played timestamp
        jdbcTemplate.update
        (
            "UPDATE team SET last_played = ?",
            OffsetDateTime.now()
                .minus(CommunityService.CURRENT_TEAM_MAX_DURATION_OFFSET)
                .minusSeconds(1)
        );
        //valid
        jdbcTemplate.update
        (
            "UPDATE team SET last_played = ?, rating = 97 WHERE id IN(1, 5, 6)",
            OffsetDateTime.now()
                .minus(CommunityService.CURRENT_TEAM_MAX_DURATION_OFFSET)
                .plusSeconds(10)
        );
        jdbcTemplate.update
        (
            "UPDATE team SET last_played = ?, rating = 98 WHERE id IN(7, 8)",
            OffsetDateTime.now()
                .minus(CommunityService.CURRENT_TEAM_MAX_DURATION_OFFSET)
                .plusSeconds(10)
        );
        //valid, newer team
        jdbcTemplate.update
        (
            "UPDATE team SET last_played = ?, rating = 99 WHERE id = 2",
            OffsetDateTime.now()
                .minus(CommunityService.CURRENT_TEAM_MAX_DURATION_OFFSET)
                .plusSeconds(11)
        );

        LocalDate bd1 = LocalDate.now().minusYears(20);
        OffsetDateTime odt = OffsetDateTime.now();
        proPlayers = new ProPlayer[]
        {
            new ProPlayer(null, 1L, "tag1", "name1", "US", bd1, 1, odt, 1),
            new ProPlayer(null, 2L, "tag2", "name2", "US", bd1.minusDays(2), 2, odt, 2),
            new ProPlayer(null, 3L, "tag3", "name3", "US", bd1.minusDays(3), 3, odt, 3),
            new ProPlayer(null, 4L, "tag4", "name4", "US", bd1.minusDays(4), 4, odt, 4),
            new ProPlayer(null, 5L, "tag5", "name5", "US", bd1.minusDays(5), 5, odt, 5),
            new ProPlayer(null, 6L, "tag6", "name6", "US", bd1.minusDays(6), 6, odt, 6),
            new ProPlayer(null, 7L, "tag7", "name7", "US", bd1.minusDays(7), 7, odt, 7)
        };
        for(ProPlayer proPlayer : proPlayers) proPlayerDAO.merge(proPlayer);
        links = new SocialMediaLink[]
        {
            new SocialMediaLink
            (
                proPlayers[0].getId(),
                SocialMedia.TWITCH,
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser1",
                OffsetDateTime.now(),
                "twitchServiceUserId1",
                false
            ),
            new SocialMediaLink
            (
                proPlayers[0].getId(),
                SocialMedia.TWITTER,
                SocialMedia.TWITTER.getBaseUserUrl() + "/twitterUser1",
                OffsetDateTime.now(),
                "twitchServiceUserId1", //same as first link for the test
                false
            ),

            new SocialMediaLink
            (
                proPlayers[1].getId(),
                SocialMedia.TWITCH,
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser2",
                OffsetDateTime.now(),
                "twitchServiceUserId2",
                false
            ),
            new SocialMediaLink
            (
                proPlayers[3].getId(),
                SocialMedia.TWITCH,
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser4",
                OffsetDateTime.now(),
                "twitchServiceUserId4",
                false
            ),
            new SocialMediaLink
            (
                proPlayers[4].getId(),
                SocialMedia.TWITCH,
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser5",
                OffsetDateTime.now(),
                "twitchServiceUserId5",
                false
            ),
            new SocialMediaLink
            (
                proPlayers[5].getId(),
                SocialMedia.TWITCH,
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser6",
                OffsetDateTime.now(),
                "twitchServiceUserId6",
                false
            ),
            new SocialMediaLink
            (
                proPlayers[6].getId(),
                SocialMedia.TWITCH,
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser7",
                OffsetDateTime.now(),
                "twitchServiceUserId7",
                false
            )
        };
        socialMediaLinkDAO.merge(Set.of(links));

        proPlayerAccountDAO.merge(Set.of(
            new ProPlayerAccount(proPlayers[0].getId(), accounts[0].getId()),
            new ProPlayerAccount(proPlayers[1].getId(), accounts[1].getId()),
            new ProPlayerAccount(proPlayers[2].getId(), accounts[2].getId()),
            new ProPlayerAccount(proPlayers[3].getId(), accounts[3].getId()),
            new ProPlayerAccount(proPlayers[4].getId(), accounts[4].getId()),
            new ProPlayerAccount(proPlayers[5].getId(), accounts[5].getId()),
            new ProPlayerAccount(proPlayers[6].getId(), accounts[6].getId())
        ));

        streams = new VideoStream[]
        {
            //linked to proPlayer[0]
            new VideoStreamImpl
            (
                SocialMedia.TWITCH,
                "twitchStreamId1",
                "twitchServiceUserId1",
                "twitchUserName1",
                "title1",
                Locale.ENGLISH,
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser1",
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser1/thumbnail",
                3
            ),
            new VideoStreamImpl
            (
                SocialMedia.TWITCH,
                "twitchStreamId4",
                "twitchServiceUserId4",
                "twitchUserName4",
                "title4",
                Locale.FRENCH,
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser4",
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser4/thumbnail",
                2
            ),
            new VideoStreamImpl
            (
                SocialMedia.TWITCH,
                "twitchStreamId5",
                "twitchServiceUserId5",
                "twitchUserName5",
                "title5",
                Locale.FRENCH,
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser5",
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser5/thumbnail",
                2
            ),
            new VideoStreamImpl
            (
                SocialMedia.TWITCH,
                "twitchStreamId6",
                "twitchServiceUserId6",
                "twitchUserName6",
                "title6",
                Locale.FRENCH,
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser6",
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser6/thumbnail",
                2
            ),
            new VideoStreamImpl
            (
                SocialMedia.TWITCH,
                "twitchStreamId7",
                "twitchServiceUserId7",
                "twitchUserName7",
                "title7",
                Locale.FRENCH,
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser7",
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser7/thumbnail",
                2
            ),
            new VideoStreamImpl
            (
                SocialMedia.TWITCH,
                "twitchStreamId3",
                "twitchServiceUserId3",
                "twitchUserName3",
                "title3",
                Locale.FRENCH,
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser3",
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser3/thumbnail",
                2
            ),
            //linked to proPlayer[1]
            new VideoStreamImpl
            (
                SocialMedia.TWITCH,
                "twitchStreamId2",
                "twitchServiceUserId2",
                "twitchUserName2",
                "title2",
                Locale.CHINESE,
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser2",
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser2/thumbnail",
                1
            )
        };
        when(videoStreamSupplier.getStreams()).thenReturn(Flux.fromArray(streams));
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
    public void testStreams()
    throws Exception
    {
        List<LadderVideoStream> ladderStreams = objectMapper.readValue(mvc.perform
        (
            get("/api/revealed/stream")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        Assertions.assertThat(ladderStreams)
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .ignoringFields("proPlayer.proPlayer.version")
            .isEqualTo(List.of(
                //fully identified stream
                new LadderVideoStream
                (
                    streams[0],
                    new LadderProPlayer
                    (
                        proPlayers[0],
                        null,
                        List.of(links[0], links[1])
                    ),
                    ladderSearchDAO.findCharacterTeams(Set.of(1L)).stream()
                        .filter(t->t.getId() == 2L)
                        .findAny()
                        .orElseThrow()
                ),
                //unidentified stream
                new LadderVideoStream
                (
                    streams[5],
                    null,
                    null
                ),
                //fully identified stream
                new LadderVideoStream
                (
                    streams[1],
                    new LadderProPlayer
                    (
                        proPlayers[3],
                        null,
                        List.of(links[3])
                    ),
                    ladderSearchDAO.findCharacterTeams(Set.of(4L)).stream()
                        .filter(t->t.getId() == 5L)
                        .findAny()
                        .orElseThrow()
                ),
                //fully identified stream
                new LadderVideoStream
                (
                    streams[2],
                    new LadderProPlayer
                    (
                        proPlayers[4],
                        null,
                        List.of(links[4])
                    ),
                    ladderSearchDAO.findCharacterTeams(Set.of(5L)).stream()
                        .filter(t->t.getId() == 6L)
                        .findAny()
                        .orElseThrow()
                ),
                //fully identified stream
                new LadderVideoStream
                (
                    streams[3],
                    new LadderProPlayer
                    (
                        proPlayers[5],
                        null,
                        List.of(links[5])
                    ),
                    ladderSearchDAO.findCharacterTeams(Set.of(6L)).stream()
                        .filter(t->t.getId() == 7L)
                        .findAny()
                        .orElseThrow()
                ),
                //fully identified stream
                new LadderVideoStream
                (
                    streams[4],
                    new LadderProPlayer
                    (
                        proPlayers[6],
                        null,
                        List.of(links[6])
                    ),
                    ladderSearchDAO.findCharacterTeams(Set.of(7L)).stream()
                        .filter(t->t.getId() == 8L)
                        .findAny()
                        .orElseThrow()
                ),
                //linked to a player, but there is no team
                new LadderVideoStream
                (
                    streams[6],
                    new LadderProPlayer
                    (
                        proPlayers[1],
                        null,
                        List.of(links[2])
                    ),
                    null
                )
            ));
    }

    @Test
    public void testFeaturedStreams()
    throws Exception
    {
        Random rng = mock(Random.class);
        when(rng.nextInt(anyInt())).thenReturn(1);
        when(randomSupplier.get()).thenReturn(rng);

        List<LadderVideoStream> featuredStreams1 = objectMapper.readValue(mvc.perform
        (
            get("/api/revealed/stream/featured")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        List<LadderVideoStream> featuredStreams = new ArrayList<>(List.of
        (
            new LadderVideoStream
            (
                streams[0],
                new LadderProPlayer
                (
                    proPlayers[0],
                    null,
                    List.of(links[0], links[1])
                ),
                ladderSearchDAO.findCharacterTeams(Set.of(1L)).stream()
                    .filter(t->t.getId() == 2L)
                    .findAny()
                    .orElseThrow(),
                CommunityService.Featured.POPULAR
            ),
            new LadderVideoStream
            (
                streams[4],
                new LadderProPlayer
                (
                    proPlayers[6],
                    null,
                    List.of(links[6])
                ),
                ladderSearchDAO.findCharacterTeams(Set.of(7L)).stream()
                    .filter(t->t.getId() == 8L)
                    .findAny()
                    .orElseThrow(),
                CommunityService.Featured.SKILLED
            ),
            //random slots are only for fully identified streams
            new LadderVideoStream
            (
                streams[2],
                new LadderProPlayer
                (
                    proPlayers[4],
                    null,
                    List.of(links[4])
                ),
                ladderSearchDAO.findCharacterTeams(Set.of(5L)).stream()
                    .filter(t->t.getId() == 6L)
                    .findAny()
                    .orElseThrow(),
                CommunityService.Featured.RANDOM
            )
        ));

        Assertions.assertThat(featuredStreams1)
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .ignoringFields("proPlayer.proPlayer.version")
            .isEqualTo(featuredStreams);

        //the same random stream is picked, despite different rng
        lenient().when(rng.nextInt(anyInt())).thenReturn(0);
        List<LadderVideoStream> featuredStreams2 = objectMapper.readValue(mvc.perform
        (
            get("/api/revealed/stream/featured")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        Assertions.assertThat(featuredStreams2)
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .ignoringFields("proPlayer.proPlayer.version")
            .isEqualTo(featuredStreams);

        //random stream slot has expired, should pick a new stream due to different rng
        communityService.setCurrentRandomStreamAssigned(
            Instant.now().minus(CommunityService.RANDOM_STREAM_MAX_DURATION).minusSeconds(1));
        featuredStreams.set
        (
            2,
            new LadderVideoStream
            (
                streams[1],
                new LadderProPlayer
                (
                    proPlayers[3],
                    null,
                    List.of(links[3])
                ),
                ladderSearchDAO.findCharacterTeams(Set.of(4L)).stream()
                    .filter(t->t.getId() == 5L)
                    .findAny()
                    .orElseThrow(),
                CommunityService.Featured.RANDOM
            )
        );
        List<LadderVideoStream> featuredStreams3 = objectMapper.readValue(mvc.perform
        (
            get("/api/revealed/stream/featured")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        Assertions.assertThat(featuredStreams3)
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .ignoringFields("proPlayer.proPlayer.version")
            .isEqualTo(featuredStreams);
    }

}
