// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = AllTestConfig.class)
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class SearchServiceIT
{

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mvc;

    @BeforeAll
    public static void init
    (
        @Autowired DataSource dataSource,
        @Autowired WebApplicationContext webApplicationContext,
        @Autowired CacheManager cacheManager,
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
            cacheManager.getCacheNames()
                .forEach(cacheName->cacheManager.getCache(cacheName).clear());

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

    private String[] getSuggestions(String term)
    throws Exception
    {
        return objectMapper.readValue(mvc.perform
        (
            get("/api/character/search/suggestions")
                .queryParam("term", term)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, WebServiceUtil.DEFAULT_CACHE_HEADER))
            .andReturn().getResponse().getContentAsString(), String[].class);
    }

    @Test
    public void testCharacterNameSuggestion()
    throws Exception
    {
        String searchTermA = "a".repeat(SearchService.MIN_CHARACTER_NAME_LENGTH);
        String[] suggestionsA = getSuggestions(searchTermA);

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
        String[] suggestionsA = getSuggestions(shortSearchTerm);
        assertEquals(0, suggestionsA.length);
    }

    @Test
    public void testBattleTagSuggestion()
    throws Exception
    {
        String[] suggestions = getSuggestions("ab#");
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
        String[] suggestions = getSuggestions(BasePlayerCharacter.DEFAULT_FAKE_FULL_NAME);
        assertEquals(0, suggestions.length);
    }

    @Test
    public void testClanTagSuggestion()
    throws Exception
    {
        String[] suggestions = getSuggestions("[a");

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
        String[] suggestions = getSuggestions("[");
        assertEquals(0, suggestions.length);
    }

}
