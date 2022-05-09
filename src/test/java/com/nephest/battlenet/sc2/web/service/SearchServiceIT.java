// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.BasePlayerCharacter;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.web.controller.CharacterController;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = AllTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class SearchServiceIT
{

    @Autowired
    private ObjectMapper objectMapper;

    private static MockMvc mvc;

    @BeforeAll
    public static void init
    (
        @Autowired DataSource dataSource,
        @Autowired WebApplicationContext webApplicationContext,
        @Autowired AccountDAO accountDAO,
        @Autowired PlayerCharacterDAO playerCharacterDAO,
        @Autowired ClanDAO clanDAO,
        @Autowired JdbcTemplate template
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));

            for(int i = 0; i < CharacterController.SEARCH_SUGGESTIONS_SIZE + 1; i++)
                accountDAO.merge(new Account(null, Partition.GLOBAL, "ab#" + i));
            accountDAO.merge(new Account(null, Partition.GLOBAL, "aa#1"));

            String charNamePrefixA = "a".repeat(SearchService.MIN_CHARACTER_NAME_LENGTH);
            String charNamePrefixB = "b".repeat(SearchService.MIN_CHARACTER_NAME_LENGTH);
            for(int i = 0; i < CharacterController.SEARCH_SUGGESTIONS_SIZE + 1; i++)
            {
                playerCharacterDAO.merge(new PlayerCharacter
                (
                    null, (long) i + 1, Region.EU, (long) i, 1,
                    charNamePrefixA + Character.toString('a' + i) + "#" + i
                ));
                template.update
                (
                    "INSERT INTO player_character_stats"
                    + "(player_character_id, queue_type, team_type, race, rating_max, league_max, games_played) "
                    + "VALUES (" + (i + 1) + ", 201, 1, 1, " + (i + 1) + ", 1, 1)"
                );
            }

            long bIx = CharacterController.SEARCH_SUGGESTIONS_SIZE + 2;
            playerCharacterDAO.merge(new PlayerCharacter(
                null, bIx, Region.EU, 999L, 1, charNamePrefixB + "a#1"));
            template.update
            (
                "INSERT INTO player_character_stats("
                    + "player_character_id, queue_type, team_type, race, rating_max, league_max, games_played) "
                    + "VALUES (" + bIx + ", 201, 1, 1, " + bIx + ", 1, 1)"
            );

            for(int i = 0; i < CharacterController.SEARCH_SUGGESTIONS_SIZE + 1; i++)
                clanDAO.merge(new Clan(null, "a" + Character.toString('a' + i), Region.EU, null));
            clanDAO.merge(new Clan(null, "b" + Character.toString('a' + (int) bIx), Region.EU, null));
            template.update("UPDATE clan SET active_members = id");
        }

        mvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .alwaysDo(print())
            .build();
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
    public void testCharacterNameSuggestion()
    throws Exception
    {
        String searchTermA = "a".repeat(SearchService.MIN_CHARACTER_NAME_LENGTH);
        String[] suggestionsA = objectMapper.readValue(mvc.perform
        (
            get("/api/character/search/{name}/suggestions", searchTermA)
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        assertEquals(CharacterController.SEARCH_SUGGESTIONS_SIZE, suggestionsA.length);
        //sorted by max rating desc
        for(int i = 0; i < suggestionsA.length; i++)
        {
            String expectedName = searchTermA
                + Character.toString('a' + (CharacterController.SEARCH_SUGGESTIONS_SIZE - i));
            assertEquals(expectedName, suggestionsA[i]);
        }

    }

    @Test
    public void testShortCharacterNameSuggestion()
    throws Exception
    {
        String shortSearchTerm = "a".repeat(SearchService.MIN_CHARACTER_NAME_LENGTH - 1);
        String[] suggestionsA = objectMapper.readValue(mvc.perform
        (
            get("/api/character/search/{name}/suggestions", shortSearchTerm)
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        assertEquals(0, suggestionsA.length);
    }

    @Test
    public void testBattleTagSuggestion()
    throws Exception
    {
        String[] suggestions = objectMapper.readValue(mvc.perform
        (
            get("/api/character/search/{name}/suggestions", "ab#")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        assertEquals(CharacterController.SEARCH_SUGGESTIONS_SIZE, suggestions.length);
        //sorted by max rating desc
        for(int i = 0; i < suggestions.length; i++)
        {
            String expectedName = "ab#" + (CharacterController.SEARCH_SUGGESTIONS_SIZE - i);
            assertEquals(expectedName, suggestions[i]);
        }
    }

    @Test
    public void testFakeBattleTagSuggestion()
    throws Exception
    {
        String[] suggestions = objectMapper.readValue(mvc.perform
        (
            get("/api/character/search/{name}/suggestions", BasePlayerCharacter.DEFAULT_FAKE_FULL_NAME)
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        assertEquals(0, suggestions.length);
    }

    @Test
    public void testClanTagSuggestion()
    throws Exception
    {
        String[] suggestions = objectMapper.readValue(mvc.perform
        (
            get("/api/character/search/{name}/suggestions", "[a")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        assertEquals(CharacterController.SEARCH_SUGGESTIONS_SIZE, suggestions.length);
        //sorted by active member count desc
        for(int i = 0; i < suggestions.length; i++)
        {
            String expectedName = "[a"
                + Character.toString('a' + (CharacterController.SEARCH_SUGGESTIONS_SIZE - i))
                + "]";
            assertEquals(expectedName, suggestions[i]);
        }
    }

    @Test
    public void testShortClanTagSuggestion()
    throws Exception
    {
        String[] suggestions = objectMapper.readValue(mvc.perform
        (
            get("/api/character/search/{name}/suggestions", "[")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        assertEquals(0, suggestions.length);
    }

}
