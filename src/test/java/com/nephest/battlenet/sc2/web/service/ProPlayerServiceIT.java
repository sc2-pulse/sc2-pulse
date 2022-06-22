// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.aligulac.AligulacProPlayer;
import com.nephest.battlenet.sc2.model.aligulac.AligulacProPlayerRoot;
import com.nephest.battlenet.sc2.model.aligulac.AligulacProTeam;
import com.nephest.battlenet.sc2.model.aligulac.AligulacProTeamRoot;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterStatsDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderProPlayer;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderCharacterDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderProPlayerDAO;
import com.nephest.battlenet.sc2.model.revealed.RevealedPlayers;
import com.nephest.battlenet.sc2.model.revealed.RevealedProPlayer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class ProPlayerServiceIT
{

    public static final QueueType QUEUE_TYPE = QueueType.LOTV_4V4;
    public static final TeamType TEAM_TYPE = TeamType.ARRANGED;
    public static final BaseLeagueTier.LeagueTierType TIER_TYPE = BaseLeagueTier.LeagueTierType.FIRST;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private SC2RevealedAPI revealedAPI;

    @Autowired
    private AligulacAPI aligulacAPI;

    @Autowired
    private LadderProPlayerDAO ladderProPlayerDAO;

    @Autowired
    private ProPlayerService proPlayerService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LadderCharacterDAO ladderCharacterDAO;

    @Autowired
    private PlayerCharacterStatsDAO playerCharacterStatsDAO;

    @BeforeAll
    public static void beforeAll
    (
        @Autowired DataSource dataSource,
        @Autowired SeasonGenerator seasonGenerator,
        @Autowired PlayerCharacterStatsDAO playerCharacterStatsDAO
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        seasonGenerator.generateDefaultSeason
        (
            List.of(Region.EU),
            List.of(BaseLeague.LeagueType.values()),
            List.of(QUEUE_TYPE),
            TEAM_TYPE,
            TIER_TYPE,
            5
        );
        playerCharacterStatsDAO.mergeCalculate();
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
    public void testUpdate()
    throws IOException
    {
        MockWebServer server = new MockWebServer();
        server.start();
        revealedAPI.setWebClient(revealedAPI.getWebClient().mutate().baseUrl(server.url("/").uri().toString()).build());
        aligulacAPI.setWebClient(aligulacAPI.getWebClient().mutate().baseUrl(server.url("/").uri().toString()).build());

        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(createRevealedProPlayers())));
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(createAligulacProPlayers1())));
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
            .setBody(objectMapper.writeValueAsString(createAligulacProPlayers2())));

        proPlayerService.setAligulacBatchSize(1); //test batching
        proPlayerService.updateRevealed(); //disabled atm, test anyway
        proPlayerService.update();

        LadderTeamMember member1 = ladderCharacterDAO.findDistinctCharacters("battletag#10").get(0).getMembers();
        assertEquals("Aligulac nickname1", member1.getProNickname());
        assertNull(member1.getProTeam());
        LadderTeamMember nullMember =ladderCharacterDAO.findDistinctCharacters("battletag#20").get(0).getMembers();
        assertNull(nullMember.getProNickname());
        assertNull(nullMember.getProTeam());

        //skip character id link
        assertNull(ladderProPlayerDAO.getProPlayerByCharacterId(2L).getProPlayer());
        LadderProPlayer ladderProPlayer = ladderProPlayerDAO.getProPlayerByBattletag("battletag#30");
        //replaced by aligulac id
        assertArrayEquals
        (
            ByteBuffer.allocate(Long.BYTES).putLong(123321L).array(),
            ladderProPlayer.getProPlayer().getRevealedId()
        );
        assertEquals(123321L, ladderProPlayer.getProPlayer().getAligulacId());
        assertEquals("Aligulac Romanized Name2", ladderProPlayer.getProPlayer().getName());
        assertEquals("Aligulac nickname2", ladderProPlayer.getProPlayer().getNickname());
        //even though revealed team is null, it is updated from the aligulac
        assertEquals("currentTeam2", ladderProPlayer.getProTeam().getName());
        assertEquals("ct2", ladderProPlayer.getProTeam().getShortName());
        assertEquals("EU", ladderProPlayer.getProPlayer().getCountry());
        assertEquals(LocalDate.of(2020, 1, 2), ladderProPlayer.getProPlayer().getBirthday());
        assertEquals(2, ladderProPlayer.getProPlayer().getEarnings());
        assertEquals(2, ladderProPlayer.getLinks().size());
        ladderProPlayer.getLinks().sort((a,b)->Comparator.comparing(SocialMedia::getId).compare(a.getType(), b.getType()));
        assertEquals(SocialMedia.ALIGULAC, ladderProPlayer.getLinks().get(0).getType());
        assertEquals("http://aliculac.com/123321-something", ladderProPlayer.getLinks().get(0).getUrl());
        assertEquals(SocialMedia.TWITCH, ladderProPlayer.getLinks().get(1).getType());
        assertEquals("https://twitch.tv/serral", ladderProPlayer.getLinks().get(1).getUrl());
    }

    private RevealedPlayers createRevealedProPlayers()
    {
        RevealedProPlayer[] players = new RevealedProPlayer[2];
        players[0] = new RevealedProPlayer
        (
            "11",
            "nickname1",
            "firstName1",
            "lastName1",
            "currentTeam1",
            new String[]{"battletag#10"},
            new Long[]{},
            Map.of
            (
                SocialMedia.ALIGULAC, "http://aliculac.com/1-something"
            ),
            Map.of("iso2", "KR")
        );
        players[1] = new RevealedProPlayer
        (
            "22",
            "nickname2",
            "firstName2",
            "lastName2",
            null,
            new String[]{"battletag#30"},
            new Long[]{2L},
            Map.of
            (
                SocialMedia.ALIGULAC, "http://aliculac.com/123321-something",
                SocialMedia.TWITCH, "https://twitch.tv/serral"
            ),
            Map.of("iso2", "US")
        );
        return new RevealedPlayers(players);
    }

    private AligulacProPlayerRoot createAligulacProPlayers1()
    {
        AligulacProPlayer[] players = new AligulacProPlayer[1];
        players[0] = new AligulacProPlayer
        (
            "Aligulac Name1", "Aligulac Romanized Name1", "Aligulac nickname1",
            LocalDate.of(2020, 1, 1),
            "KR",
            1,
            new AligulacProTeamRoot[]{}
        );
        return new AligulacProPlayerRoot(players);
    }

    private AligulacProPlayerRoot createAligulacProPlayers2()
    {
        AligulacProPlayer[] players = new AligulacProPlayer[1];
        players[0] = new AligulacProPlayer
        (
            "Aligulac Name2", "Aligulac Romanized Name2", "Aligulac nickname2",
            LocalDate.of(2020, 1, 2),
            "EU",
            2,
            new AligulacProTeamRoot[]{new AligulacProTeamRoot(new AligulacProTeam(1L, "currentTeam2", "ct2"))}
        );
        return new AligulacProPlayerRoot(players);
    }

}
