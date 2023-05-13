// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
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
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.SocialMediaLink;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerDAO;
import com.nephest.battlenet.sc2.model.local.dao.SocialMediaLinkDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderProPlayer;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderCharacterDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderProPlayerDAO;
import com.nephest.battlenet.sc2.model.revealed.RevealedPlayers;
import com.nephest.battlenet.sc2.model.revealed.RevealedProPlayer;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest(classes = AllTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class ProPlayerServiceIT
{

    public static final QueueType QUEUE_TYPE = QueueType.LOTV_4V4;
    public static final TeamType TEAM_TYPE = TeamType.ARRANGED;
    public static final BaseLeagueTier.LeagueTierType TIER_TYPE = BaseLeagueTier.LeagueTierType.FIRST;

    @Autowired
    private ProPlayerDAO proPlayerDAO;

    @Autowired
    private SocialMediaLinkDAO socialMediaLinkDAO;

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

    @BeforeEach
    public void beforeEach
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

    @Test @SuppressWarnings("deprecation")
    public void testUpdate()
    throws IOException
    {
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
        WebClient revealedWebClient = revealedAPI.getWebClient();
        WebClient aligulacWebClient = aligulacAPI.getWebClient();
        try(MockWebServer server = new MockWebServer())
        {
            server.start();
            revealedAPI.setWebClient(revealedAPI.getWebClient().mutate().baseUrl(server.url("/").uri().toString()).build());
            aligulacAPI.setWebClient(aligulacAPI.getWebClient().mutate().baseUrl(server.url("/").uri().toString()).build());

            server.enqueue(new MockResponse().setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody(objectMapper.writeValueAsString(createRevealedProPlayers())));
            server.enqueue(new MockResponse().setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody(objectMapper.writeValueAsString(createAligulacProPlayers1())));
            server.enqueue(new MockResponse().setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody(objectMapper.writeValueAsString(createAligulacProPlayers2())));

            proPlayerService.setAligulacBatchSize(1); //test batching
            proPlayerService.updateRevealed(); //deprecated
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
            assertEquals(123321L, ladderProPlayer.getProPlayer().getAligulacId());
            assertEquals("Aligulac Romanized Name2", ladderProPlayer.getProPlayer().getName());
            assertEquals("Aligulac nickname2", ladderProPlayer.getProPlayer().getNickname());
            //even though revealed team is null, it is updated from the aligulac
            assertEquals("currentTeam2", ladderProPlayer.getProTeam().getName());
            assertEquals("ct2", ladderProPlayer.getProTeam().getShortName());
            assertEquals("EU", ladderProPlayer.getProPlayer().getCountry());
            assertEquals(LocalDate.of(2020, 1, 2), ladderProPlayer.getProPlayer().getBirthday());
            assertEquals(2, ladderProPlayer.getProPlayer().getEarnings());
            assertEquals(3, ladderProPlayer.getLinks().size());
            ladderProPlayer.getLinks().sort((a,b)->Comparator.comparing(SocialMedia::getId).compare(a.getType(), b.getType()));
            assertEquals(SocialMedia.ALIGULAC, ladderProPlayer.getLinks().get(0).getType());
            assertEquals("http://aligulac.com/players/123321", ladderProPlayer.getLinks().get(0).getUrl());
            assertEquals(SocialMedia.TWITCH, ladderProPlayer.getLinks().get(1).getType());
            assertEquals("https://twitch.tv/serral", ladderProPlayer.getLinks().get(1).getUrl());
            assertEquals(SocialMedia.LIQUIPEDIA, ladderProPlayer.getLinks().get(2).getType());
            assertEquals("https://liquipedia.net/starcraft2/Lpname2", ladderProPlayer.getLinks().get(2).getUrl());

            server.enqueue(new MockResponse().setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody(objectMapper.writeValueAsString(createAligulacProPlayers1())));
            server.enqueue(new MockResponse().setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody(objectMapper.writeValueAsString(createAligulacProPlayers2NoTeam())));
            proPlayerService.update();
            LadderProPlayer ladderProPlayer2 = ladderProPlayerDAO.getProPlayerByBattletag("battletag#30");
            assertNull(ladderProPlayer2.getProTeam());
            server.shutdown();
        }
        finally
        {
            revealedAPI.setWebClient(revealedWebClient);
            aligulacAPI.setWebClient(aligulacWebClient);
        }
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
            1L,
            "Aligulac Name1", "Aligulac Romanized Name1", "Aligulac nickname1", "Lpname1",
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
            123321L,
            "Aligulac Name2", "Aligulac Romanized Name2", "Aligulac nickname2", "Lpname2",
            LocalDate.of(2020, 1, 2),
            "EU",
            2,
            new AligulacProTeamRoot[]{new AligulacProTeamRoot(new AligulacProTeam(1L, "currentTeam2", "ct2"))}
        );
        return new AligulacProPlayerRoot(players);
    }

    private AligulacProPlayerRoot createAligulacProPlayers2NoTeam()
    {
        AligulacProPlayerRoot root = createAligulacProPlayers2();
        root.getObjects()[0].setCurrentTeams(new AligulacProTeamRoot[0]);
        return root;
    }

    @Test
    public void testUpdateSocialMediaLinks()
    {
        ProPlayer proPlayer = proPlayerDAO.merge(new ProPlayer(null, 1L, "tag", "name"));
        ProPlayer proPlayer2 = proPlayerDAO.merge(new ProPlayer(null, 2L, "tag2", "name2"));
        ProPlayer proPlayer3 = proPlayerDAO.merge(new ProPlayer(null, 3L, "tag3", "name3"));
        socialMediaLinkDAO.merge
        (
            false,
            new SocialMediaLink
            (
                proPlayer2.getId(),
                SocialMedia.LIQUIPEDIA,
                "https://liquipedia.net/starcraft2/Serral"
            ),
            new SocialMediaLink
            (
                proPlayer2.getId(),
                SocialMedia.TWITTER,
                "oldTwitterLink"
            ),

            new SocialMediaLink
            (
                proPlayer3.getId(),
                SocialMedia.LIQUIPEDIA,
                "https://liquipedia.net/starcraft2/Maru"
            )
        );
        assertTrue(proPlayerService.updateSocialMediaLinks().block() >= 2);

        //links were updated
        List<SocialMediaLink> serralLinks = socialMediaLinkDAO.find(proPlayer2.getId());
        assertTrue(serralLinks.size() > 1);

        verifyTypePresent(serralLinks, SocialMedia.TWITCH);
        verifyTypePresent(serralLinks, SocialMedia.DISCORD);
        SocialMediaLink twitterLink = verifyTypePresent(serralLinks, SocialMedia.TWITTER);
        assertNotEquals("oldTwitterLink", twitterLink.getUrl());

        //aligulac links shouldn't be updated even if they present in the upstream API
        Optional<SocialMediaLink> aligulacLink = serralLinks.stream()
            .filter(l->l.getType() == SocialMedia.ALIGULAC)
            .findAny();
        assertTrue(aligulacLink.isEmpty());

        List<SocialMediaLink> maruLinks = socialMediaLinkDAO.find(proPlayer3.getId());
        assertTrue(maruLinks.size() > 1);

        verifyTypePresent(maruLinks, SocialMedia.INSTAGRAM);
        verifyTypePresent(maruLinks, SocialMedia.TWITCH);
    }

    private <T extends SocialMediaLink> T verifyTypePresent
    (
        Collection<T> links,
        SocialMedia type)
    {
        Optional<T> link = links.stream()
            .filter(l->l.getType() == type)
            .findAny();
        assertTrue(link.isPresent());
        return link.get();
    }

}
