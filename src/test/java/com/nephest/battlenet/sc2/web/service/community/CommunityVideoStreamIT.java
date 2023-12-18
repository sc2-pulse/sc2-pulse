// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.community;

import static com.nephest.battlenet.sc2.web.service.community.CommunityService.CURRENT_FEATURED_TEAM_MAX_DURATION_OFFSET;
import static com.nephest.battlenet.sc2.web.service.community.CommunityService.CURRENT_TEAM_MAX_DURATION_OFFSET;
import static com.nephest.battlenet.sc2.web.service.community.CommunityService.FEATURED_STREAM_SKILLED_SLOT_COUNT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
    private ProPlayerDAO proPlayerDAO;

    @Autowired
    private ProPlayerAccountDAO proPlayerAccountDAO;

    @Autowired
    private SocialMediaLinkDAO socialMediaLinkDAO;

    @Autowired
    private CommunityService communityService;

    @Autowired
    private LadderSearchDAO ladderSearchDAO;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean(classes = {TwitchVideoStreamSupplier.class})
    private VideoStreamSupplier videoStreamSupplier;

    @MockBean(classes = {BilibiliVideoStreamSupplier.class})
    private VideoStreamSupplier otherStreamSupplier;

    @MockBean
    private ThreadLocalRandomSupplier randomSupplier;

    private SocialMediaLink[] links;
    private VideoStream[] streams;
    private ProPlayer[] proPlayers;

    @BeforeEach
    public void beforeEach
    (
        @Autowired DataSource dataSource
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
            when(otherStreamSupplier.getStreams()).thenReturn(Flux.empty());
        }
    }

    private void init
    (
        int count, 
        BiConsumer<PlayerCharacter[], List<PlayerCharacter>> teamCustomizer
    )
    {
        seasonGenerator.generateDefaultSeason(0);

        Account[] accounts = seasonGenerator.generateAccounts(Partition.GLOBAL, "btag", count);
        PlayerCharacter[] characters = seasonGenerator.generateCharacters("char", accounts, Region.EU, 1L);
        ArrayList<PlayerCharacter> teamCharacters = new ArrayList<>(Arrays.asList(characters));
        teamCustomizer.accept(characters, teamCharacters);
        List<Team> teams = seasonGenerator
            .createTeams(teamCharacters.toArray(PlayerCharacter[]::new));

        LocalDate bd1 = LocalDate.now().minusYears(20);
        OffsetDateTime odt = OffsetDateTime.now();
        proPlayers = IntStream.range(0, count)
            .boxed()
            .map(i->new ProPlayer(null, (long) i, "tag" + i, "name" + i, "US",
                bd1.minusDays(i), i, odt, i))
            .toArray(ProPlayer[]::new);
        for(ProPlayer proPlayer : proPlayers) proPlayerDAO.merge(proPlayer);
        links = IntStream.range(0, count)
            .boxed()
            .map(i->new SocialMediaLink(
                proPlayers[i].getId(),
                SocialMedia.TWITCH,
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser" + i,
                OffsetDateTime.now(),
                "twitchServiceUserId" + i,
                false
            ))
            .toArray(SocialMediaLink[]::new);
        socialMediaLinkDAO.merge(Set.of(links));

        proPlayerAccountDAO.merge(IntStream.range(0, count)
            .boxed()
            .map(i->new ProPlayerAccount(proPlayers[i].getId(), accounts[i].getId()))
            .collect(Collectors.toSet())
        );
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

    @AfterEach
    public void afterEach()
    {
        communityService.resetRandomFeaturedStream();
    }

    @Test
    public void testStreams()
    throws Exception
    {
        init(3, (c, c1)->{});
        jdbcTemplate.update
        (
            "UPDATE team SET last_played = ?",
            OffsetDateTime.now()
                .minus(CURRENT_TEAM_MAX_DURATION_OFFSET)
                .plusSeconds(10)
        );

        streams = new VideoStream[]
        {
            new VideoStreamImpl
            (
                SocialMedia.TWITCH,
                "twitchStreamId1",
                "twitchServiceUserId1",
                "twitchUserName1",
                "title1",
                Locale.ENGLISH,
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser1",
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser1/profile",
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser1/thumbnail",
                3
            ),
            new VideoStreamImpl
            (
                SocialMedia.TWITCH,
                "twitchStreamId2",
                "twitchServiceUserId2",
                "twitchUserName2",
                "title2",
                Locale.FRENCH,
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser2",
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser2/profile",
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser2/thumbnail",
                2
            ),
            //identified, but no team
            new VideoStreamImpl
            (
                SocialMedia.TWITCH,
                "twitchStreamId0",
                "twitchServiceUserId0",
                "twitchUserName0",
                "title0",
                Locale.ENGLISH,
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser0",
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser0/profile",
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser0/thumbnail",
                2
            )
        };
        when(videoStreamSupplier.getStreams()).thenReturn(Flux.fromArray(streams));
        
        CommunityStreamResult ladderStreams = objectMapper.readValue(mvc.perform
        (
            get("/api/revealed/stream")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        Assertions.assertThat(ladderStreams)
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .ignoringFields("streams.proPlayer.proPlayer.version")
            .isEqualTo(new CommunityStreamResult(List.of(
                new LadderVideoStream
                (
                    streams[0],
                    new LadderProPlayer
                    (
                        proPlayers[1],
                        null,
                        List.of(links[1])
                    ),
                    ladderSearchDAO.findCharacterTeams(Set.of(2L)).stream()
                        .findAny()
                        .orElseThrow()
                ),
                new LadderVideoStream
                (
                    streams[2],
                    new LadderProPlayer
                    (
                        proPlayers[0],
                        null,
                        List.of(links[0])
                    ),
                    ladderSearchDAO.findCharacterTeams(Set.of(1L)).stream()
                        .findAny()
                        .orElseThrow()
                ),
                new LadderVideoStream
                (
                    streams[1],
                    new LadderProPlayer
                    (
                        proPlayers[2],
                        null,
                        List.of(links[2])
                    ),
                    ladderSearchDAO.findCharacterTeams(Set.of(3L)).stream()
                        .findAny()
                        .orElseThrow()
                )
            ), Set.of()));
    }
    
    @Test
    public void whenPlayerHasMultipleTeams_thenUseMostRecentTeam()
    throws Exception
    {
        //3 teams
        init(1, (chars, teamChars)->{
            teamChars.add(chars[0]);
            teamChars.add(chars[0]);
        });
        jdbcTemplate.update
        (
            "UPDATE team SET last_played = ? WHERE id = 1",
            OffsetDateTime.now()
                .minus(CURRENT_TEAM_MAX_DURATION_OFFSET)
                .plusSeconds(10)
        );
        //most recent
        jdbcTemplate.update
        (
            "UPDATE team SET last_played = ? WHERE id = 2",
            OffsetDateTime.now()
                .minus(CURRENT_TEAM_MAX_DURATION_OFFSET)
                .plusSeconds(12)
        );
        jdbcTemplate.update
        (
            "UPDATE team SET last_played = ? WHERE id = 3",
            OffsetDateTime.now()
                .minus(CURRENT_TEAM_MAX_DURATION_OFFSET)
                .plusSeconds(11)
        );
        
        VideoStream stream = new VideoStreamImpl
        (
            SocialMedia.TWITCH,
            "twitchStreamId0",
            "twitchServiceUserId0",
            "twitchUserName0",
            "title0",
            Locale.ENGLISH,
            SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser0",
            SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser0/profile",
            SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser0/thumbnail",
            0
        );
        when(videoStreamSupplier.getStreams()).thenReturn(Flux.just(stream));

        CommunityStreamResult ladderStreams = objectMapper.readValue(mvc.perform
        (
            get("/api/revealed/stream")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        Assertions.assertThat(ladderStreams)
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .ignoringFields("streams.proPlayer.proPlayer.version")
            .isEqualTo(new CommunityStreamResult(List.of(
                new LadderVideoStream
                (
                    stream,
                    new LadderProPlayer
                    (
                        proPlayers[0],
                        null,
                        List.of(links[0])
                    ),
                    ladderSearchDAO.findCharacterTeams(Set.of(1L)).stream()
                        .filter(t->t.getId() == 2L)
                        .findAny()
                        .orElseThrow()
                )
            ), Set.of()));
    }

    public static VideoStream createIndexedVideoStream(int ix)
    {
        return new VideoStreamImpl
        (
            SocialMedia.TWITCH,
            "twitchStreamId" + ix,
            "twitchServiceUserId" + ix,
            "twitchUserName" + ix,
            "title" + ix,
            Locale.ENGLISH,
            SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser" + ix,
            SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser" + ix + "/profile",
            SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser" + ix + "/thumbnail",
            ix
        );
    }

    private LadderVideoStream createIndexedLadderVideoStream(int ix, CommunityService.Featured featured)
    {
        return new LadderVideoStream
        (
            streams[ix],
            new LadderProPlayer
            (
                proPlayers[ix],
                null,
                List.of(links[ix])
            ),
            ladderSearchDAO.findCharacterTeams(Set.of(ix + 1L)).stream()
                .findAny()
                .orElseThrow(),
            featured
        );
    }

    @Test
    public void testFeaturedSkilledStreams()
    throws Exception
    {
        init(10, (c, c1)->{});
        jdbcTemplate.update
        (
            "UPDATE team SET last_played = ?",
            OffsetDateTime.now()
                .minus(CURRENT_FEATURED_TEAM_MAX_DURATION_OFFSET)
                .plusSeconds(10)
        );
        streams = IntStream.range(0, 10)
            .boxed()
            .map(CommunityVideoStreamIT::createIndexedVideoStream)
            .toArray(VideoStream[]::new);
        streams[streams.length - 1] = streams[0];
        when(videoStreamSupplier.getStreams()).thenReturn(Flux.fromArray(streams));

        CommunityStreamResult featuredStreams1 = objectMapper.readValue(mvc.perform
        (
            get("/api/revealed/stream/featured")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        List<LadderVideoStream> featuredStreams = IntStream.range(9 - 5, 9)
            .mapToObj(i->createIndexedLadderVideoStream(i, CommunityService.Featured.SKILLED))
            .sorted(Comparator.comparing(s->s.getTeam().getId(), Comparator.reverseOrder()))
            .collect(Collectors.toList());

        Assertions.assertThat(featuredStreams1)
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .ignoringFields("streams.proPlayer.proPlayer.version")
            .isEqualTo(new CommunityStreamResult(featuredStreams, Set.of()));
    }

    private List<LadderVideoStream> testFeaturedStreamsStart(Random rng)
    throws Exception
    {
        init(10, (c, c1)->{});
        when(rng.nextInt(anyInt())).thenReturn(1);
        when(randomSupplier.get()).thenReturn(rng);

        jdbcTemplate.update
        (
            "UPDATE team SET last_played = ?",
            OffsetDateTime.now()
                .minus(CURRENT_FEATURED_TEAM_MAX_DURATION_OFFSET)
                .plusSeconds(10)
        );
        //too old for the random slot
        jdbcTemplate.update
        (
            "UPDATE team SET last_played = ? WHERE id = " + (8 - FEATURED_STREAM_SKILLED_SLOT_COUNT),
            OffsetDateTime.now()
                .minus(CURRENT_FEATURED_TEAM_MAX_DURATION_OFFSET)
                .minusSeconds(1)
        );
        streams = IntStream.range(0, 10)
            .boxed()
            .map(CommunityVideoStreamIT::createIndexedVideoStream)
            .toArray(VideoStream[]::new);
        when(videoStreamSupplier.getStreams()).thenReturn(Flux.fromArray(streams));

        assertNull(communityService.getCurrentRandomStreamAssigned());
        Instant beforeRandomStreamReassignment1 = Instant.now();
        CommunityStreamResult featuredStreams1 = objectMapper.readValue(mvc.perform
        (
            get("/api/revealed/stream/featured")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        List<LadderVideoStream> featuredStreams = new ArrayList<>(List.of
        (
            createIndexedLadderVideoStream(9, CommunityService.Featured.POPULAR),
            createIndexedLadderVideoStream(8, CommunityService.Featured.SKILLED),
            createIndexedLadderVideoStream(7, CommunityService.Featured.SKILLED),
            createIndexedLadderVideoStream(6, CommunityService.Featured.SKILLED),
            createIndexedLadderVideoStream
            (
                6 - FEATURED_STREAM_SKILLED_SLOT_COUNT,
                CommunityService.Featured.RANDOM
            )
        ));

        Assertions.assertThat(featuredStreams1)
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .ignoringFields("streams.proPlayer.proPlayer.version")
            .isEqualTo(new CommunityStreamResult(featuredStreams, Set.of()));
        assertTrue(beforeRandomStreamReassignment1
            .isBefore(communityService.getCurrentRandomStreamAssigned()));
        return featuredStreams;
    }

    @Disabled("Popular and random slots are disabled")
    @Test
    public void testFeaturedStreams()
    throws Exception
    {
        Random rng = mock(Random.class);
        List<LadderVideoStream> featuredStreams = testFeaturedStreamsStart(rng);
        //the same random stream is picked, despite different rng
        Instant maxRandomStreamInstant = Instant.now()
            .minus(CommunityService.RANDOM_STREAM_MAX_DURATION)
            .plusSeconds(5); //offset for test execution
        communityService.setCurrentRandomStreamAssigned(maxRandomStreamInstant);
        lenient().when(rng.nextInt(anyInt())).thenReturn(0);
        CommunityStreamResult featuredStreams2 = objectMapper.readValue(mvc.perform
        (
            get("/api/revealed/stream/featured")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        Assertions.assertThat(featuredStreams2)
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .ignoringFields("streams.proPlayer.proPlayer.version")
            .isEqualTo(new CommunityStreamResult(featuredStreams, Set.of()));
        assertEquals(maxRandomStreamInstant, communityService.getCurrentRandomStreamAssigned());

        //random stream slot has expired, should pick a new stream due to different rng
        communityService.setCurrentRandomStreamAssigned(
            Instant.now().minus(CommunityService.RANDOM_STREAM_MAX_DURATION).minusSeconds(1));
        featuredStreams.set
        (
            featuredStreams.size() - 1,
            createIndexedLadderVideoStream
            (
                8 - FEATURED_STREAM_SKILLED_SLOT_COUNT,
                CommunityService.Featured.RANDOM
            )
        );
        Instant beforeRandomStreamReassignment2 = Instant.now();
        CommunityStreamResult featuredStreams3 = objectMapper.readValue(mvc.perform
        (
            get("/api/revealed/stream/featured")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        Assertions.assertThat(featuredStreams3)
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .ignoringFields("streams.proPlayer.proPlayer.version")
            .isEqualTo(new CommunityStreamResult(featuredStreams, Set.of()));
        assertTrue(beforeRandomStreamReassignment2
            .isBefore(communityService.getCurrentRandomStreamAssigned()));
    }

    @Disabled("Popular and random slots are disabled")
    @Test
    public void whenSameFeaturedRandomStreamLostTeam_thenPickAnotherStream()
    throws Exception
    {
        Random rng = mock(Random.class);
        List<LadderVideoStream> featuredStreams = testFeaturedStreamsStart(rng);

        featuredStreams.set
        (
            featuredStreams.size() - 1,
            createIndexedLadderVideoStream
            (
                5 - FEATURED_STREAM_SKILLED_SLOT_COUNT,
                CommunityService.Featured.RANDOM
            )
        );
        jdbcTemplate.update
        (
            "UPDATE team SET last_played = ? WHERE id = " + (7 - FEATURED_STREAM_SKILLED_SLOT_COUNT),
            OffsetDateTime.now()
                .minus(CommunityService.CURRENT_FEATURED_TEAM_MAX_DURATION_OFFSET)
                .minusSeconds(10)
        );
        CommunityStreamResult featuredStreams2 = objectMapper.readValue(mvc.perform
        (
            get("/api/revealed/stream/featured")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        Assertions.assertThat(featuredStreams2)
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .ignoringFields("streams.proPlayer.proPlayer.version")
            .isEqualTo(new CommunityStreamResult(featuredStreams, Set.of()));
    }

    @Test
    public void whenStreamProviderReturnsError_thenAddErrorInfoAndIgnoreIt()
    {
        List<VideoStream> streams = List.of
        (
            new VideoStreamImpl
            (
                SocialMedia.TWITCH,
                "twitchStreamId1",
                "twitchServiceUserId1",
                "twitchUserName1",
                "title1",
                Locale.ENGLISH,
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser1",
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser1/profile",
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser1/thumbnail",
                3
            ),
            new VideoStreamImpl
            (
                SocialMedia.TWITCH,
                "twitchStreamId2",
                "twitchServiceUserId2",
                "twitchUserName2",
                "title2",
                Locale.FRENCH,
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser2",
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser2/profile",
                SocialMedia.TWITCH.getBaseUserUrl() + "/twitchUser2/thumbnail",
                2
            )
        );
        when(videoStreamSupplier.getStreams()).thenReturn(Flux.fromIterable(streams));
        when(otherStreamSupplier.getStreams())
            .thenReturn(Flux.error(new IllegalStateException("test")));
        when(otherStreamSupplier.getService()).thenReturn(SocialMedia.BILIBILI);
        Assertions.assertThat(communityService.getStreams().block())
            .usingRecursiveComparison()
            .isEqualTo(new CommunityStreamResult(
                streams.stream()
                    .map(stream->new LadderVideoStream(stream, null, null))
                    .collect(Collectors.toList()),
                EnumSet.of(SocialMedia.BILIBILI)
            ));
    }

}
