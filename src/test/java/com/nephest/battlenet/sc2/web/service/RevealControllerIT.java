// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import com.nephest.battlenet.sc2.config.security.WithBlizzardMockUser;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.aligulac.AligulacProPlayer;
import com.nephest.battlenet.sc2.model.aligulac.AligulacProPlayerRoot;
import com.nephest.battlenet.sc2.model.aligulac.AligulacProTeam;
import com.nephest.battlenet.sc2.model.aligulac.AligulacProTeamRoot;
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.ProPlayerAccount;
import com.nephest.battlenet.sc2.model.local.ProTeam;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.SocialMediaLink;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerAccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderProPlayer;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderProPlayerDAO;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import reactor.core.publisher.Mono;

@SpringBootTest(classes = AllTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class RevealControllerIT
{

    @Autowired
    private ProPlayerDAO proPlayerDAO;

    @Autowired
    private ProPlayerAccountDAO proPlayerAccountDAO;

    @Autowired
    private LadderProPlayerDAO ladderProPlayerDAO;

    @Autowired
    private ProPlayerService proPlayerService;

    @Autowired
    private AligulacAPI realApi;

    private AligulacAPI mockApi;

    @Autowired
    private SeasonGenerator generator;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mvc;

    @BeforeEach
    public void beforeEach
    (
        @Autowired DataSource dataSource,
        @Autowired WebApplicationContext webApplicationContext
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity())
            .alwaysDo(print()).build();
        mockApi = mock(AligulacAPI.class);
        proPlayerService.setAligulacAPI(mockApi);
    }

    @AfterEach
    public void afterEach(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
        proPlayerService.setAligulacAPI(realApi);
    }

    @Test
    @WithBlizzardMockUser
    (
        partition =  Partition.GLOBAL,
        username = "user",
        roles =
        {
            SC2PulseAuthority.USER, SC2PulseAuthority.REVEALER
        }
    )
    public void testAccountRevealing()
    throws Exception
    {
        generator.generateDefaultSeason
        (
            List.of(Region.EU),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            10
        );

        ProPlayer[] playersFound1 = objectMapper.readValue(mvc.perform
        (
            get("/api/reveal/players")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), ProPlayer[].class);
        assertEquals(0, playersFound1.length);

        ProPlayer[] players = new ProPlayer[]
        {
            new ProPlayer(null, 1L, "tag1", "name1"),
            new ProPlayer(null, 2L, "tag2", "name2"),
            new ProPlayer(null, 3L, "tag3", "name3"),
        };
        proPlayerDAO.mergeWithoutIds(players);

        ProPlayer[] playersFound2 = objectMapper.readValue(mvc.perform
        (
            get("/api/reveal/players")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), ProPlayer[].class);
        assertEquals(players.length, playersFound2.length);
        for(int i = 0; i < players.length; i++) assertEquals(players[i], playersFound2[i]);

        mvc.perform
        (
            post("/api/reveal/5/2")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
            .andExpect(status().isOk())
            .andReturn();
        ProPlayerAccount proPlayerAccount = proPlayerAccountDAO.findByProPlayerId(2).get(0);
        assertEquals(5L, proPlayerAccount.getAccountId());
        assertEquals(1L, proPlayerAccount.getRevealerAccountId());

        mvc.perform
        (
            delete("/api/reveal/5/2")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
            .andExpect(status().isOk())
            .andReturn();
        assertTrue(proPlayerAccountDAO.findByProPlayerId(2).isEmpty());
    }

    @Test
    @WithBlizzardMockUser
    (
        partition =  Partition.GLOBAL,
        username = "user",
        roles =
        {
            SC2PulseAuthority.USER,
            SC2PulseAuthority.REVEALER
        }
    )
    public void testImportAligulacProfile()
    throws Exception
    {
        AligulacProPlayer aligulacProPlayer = new AligulacProPlayer
        (
            10L,
            "name",
            "romanizedName",
            "tag",
            "lpName",
            LocalDate.now(),
            "FI",
            10000,
            new AligulacProTeamRoot[]
            {
                new AligulacProTeamRoot
                (
                    new AligulacProTeam(1L, "teamName", "teamShortName")
                )
            }
        );
        when(mockApi.getPlayers(10L))
            .thenReturn(Mono.just(new AligulacProPlayerRoot(new AligulacProPlayer[]{aligulacProPlayer})));

        ProPlayer importedPlayer = objectMapper.readValue(mvc.perform
        (
            post("/api/reveal/import")
                .param("url", "http://aligulac.com/players/10-Serral/")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), ProPlayer.class);
        assertNotNull(importedPlayer.getId());
        assertEquals(10L, importedPlayer.getAligulacId());
        assertEquals("romanizedName", importedPlayer.getName());
        assertEquals("tag", importedPlayer.getNickname());
        assertEquals("FI", importedPlayer.getCountry());
        assertEquals(10000, importedPlayer.getEarnings());

        generator.generateDefaultSeason
        (
            List.of(Region.EU),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            1
        );
        proPlayerAccountDAO.merge(false, new ProPlayerAccount(importedPlayer.getId(), 1L));
        LadderProPlayer ladderProPlayer = ladderProPlayerDAO.getProPlayerByCharacterId(1L);

        List<SocialMediaLink> links = ladderProPlayer.getLinks();
        assertEquals(2, links.size());
        links.sort(Comparator.comparing(SocialMediaLink::getType));

        SocialMediaLink aligulacLink = links.get(0);
        assertEquals(SocialMedia.ALIGULAC, aligulacLink.getType());
        assertEquals("http://aligulac.com/players/10", aligulacLink.getUrl());

        SocialMediaLink liquipediaLink = links.get(1);
        assertEquals(SocialMedia.LIQUIPEDIA, liquipediaLink.getType());
        assertEquals("https://liquipedia.net/starcraft2/lpName", liquipediaLink.getUrl());

        ProTeam proTeam = ladderProPlayer.getProTeam();
        assertEquals("teamName", proTeam.getName());
        assertEquals("teamShortName", proTeam.getShortName());
    }

    @Test
    @WithBlizzardMockUser(partition = Partition.GLOBAL, username = "name")
    public void whenNotRevealer_thenForbidden()
    throws Exception
    {
        mvc.perform
        (
            get("/api/reveal/players")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isForbidden());
        mvc.perform
        (
            post("/api/reveal/5/2")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
            .andExpect(status().isForbidden());
        mvc.perform
        (
            delete("/api/reveal/5/2")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
            .andExpect(status().isForbidden());
        mvc.perform
        (
            post("/api/reveal/import")
                .param("url", "http://aligulac.com/players/485-Serral/")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
            .andExpect(status().isForbidden());
    }

}