// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.ladder.PagedSearchResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AllTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class ClanSearchIT
{

    private static final int CLAN_COUNT = ClanDAO.PAGE_SIZE * 2;
    private static MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    public static void beforeAll
    (
        @Autowired DataSource dataSource,
        @Autowired WebApplicationContext webApplicationContext,
        @Autowired JdbcTemplate template,
        @Autowired ClanDAO clanDAO
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));

            Clan[] clans = new Clan[CLAN_COUNT];
            for(int i = 0; i < CLAN_COUNT; i++)
                clans[i] = new Clan(null, "clan" + i, Region.EU, "clan" + i + "Name");
            clanDAO.merge(clans);
            template.execute
            (
                "UPDATE clan "
                    + "SET active_members = id, "
                    + "avg_rating = id + 1, "
                    + "members = id + 3, "
                    + "games = id * 2"
            );

            mvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .alwaysDo(print())
                .build();
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

    @Test
    public void testIdsSearch()
    throws Exception
    {
        Clan[] clans = objectMapper.readValue(mvc.perform
        (
            get("/api/clan/id/1,5,7")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        assertEquals(3, clans.length);
        Arrays.sort(clans, Comparator.comparing(Clan::getId));
        assertEquals(1, clans[0].getId());
        assertEquals(5, clans[1].getId());
        assertEquals(7, clans[2].getId());
    }

    @Test
    public void testTagSearch()
    throws Exception
    {
        Clan[] clans = objectMapper.readValue(mvc.perform
        (
            get("/api/clan/tag/clan1")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        assertEquals(1, clans.length);
        Clan clan = clans[0];
        assertNotNull(clan);
        assertEquals(2, clan.getId());
        assertEquals("clan1", clan.getTag());
        assertEquals("clan1Name", clan.getName());
    }

    @Test
    public void testTagOrNameSearch()
    throws Exception
    {
        Clan[] clansByTag = objectMapper.readValue(mvc.perform
        (
            get("/api/clan/tag-or-name/clan" + (CLAN_COUNT - 1))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        assertEquals(1, clansByTag.length);
        Clan clan = clansByTag[0];
        assertNotNull(clan);
        assertEquals(CLAN_COUNT, clan.getId());
        assertEquals("clan" + (CLAN_COUNT - 1), clan.getTag());
        assertEquals("clan" + (CLAN_COUNT - 1) + "Name", clan.getName());

        Clan[] clansByName = objectMapper.readValue(mvc.perform
        (
            get("/api/clan/tag-or-name/cLAn") //case-insensitive
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        assertEquals(CLAN_COUNT, clansByName.length);

        //LIKE search must be used only for names that are at least 3 characters long.
        Clan[] clansByShortName = objectMapper.readValue(mvc.perform
        (
            get("/api/clan/tag-or-name/cl")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        assertEquals(0, clansByShortName.length);
    }

    @CsvSource
    ({
        "ACTIVE_MEMBERS, " + CLAN_COUNT + ", 1, " + CLAN_COUNT,
        "AVG_RATING, " + CLAN_COUNT + 1 + ", 2, " + CLAN_COUNT,
        "MEMBERS, " + CLAN_COUNT + 2 + ", 3, " + CLAN_COUNT
    })
    @ParameterizedTest
    public void testCursorSearch(ClanDAO.Cursor cursor, int max, int min, int clanCount)
    throws Exception
    {
        //normal, first page
        PagedSearchResult<List<Clan>> result = objectMapper.readValue(mvc.perform
        (
            get("/api/clan/cursor/{cursor}/{maxCursor}/{maxCursor}/0/1", cursor, max + 1, max + 1)
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        assertEquals(ClanDAO.PAGE_SIZE, result.getResult().size());
        for(int i = 0; i < ClanDAO.PAGE_SIZE; i++)
            assertEquals(clanCount - i, result.getResult().get(i).getId());

        //reversed, last page
        PagedSearchResult<List<Clan>> reversedResult = objectMapper.readValue(mvc.perform
        (
            get("/api/clan/cursor/{cursor}/{minCursor}/{minCursor}/2/-1", cursor, min - 1, min - 1)
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        assertEquals(ClanDAO.PAGE_SIZE, reversedResult.getResult().size());
        for(int i = 0; i < ClanDAO.PAGE_SIZE; i++)
            assertEquals(ClanDAO.PAGE_SIZE - i, reversedResult.getResult().get(i).getId());

        //filtered by active member count
        PagedSearchResult<List<Clan>> filteredByActiveMembersResult = objectMapper.readValue(mvc.perform
        (
            get("/api/clan/cursor/{cursor}/{maxCursor}/{maxCursor}/0/1"
                    + "?minActiveMembers={min}&maxActiveMembers={max}",
                cursor, max, max, 10, 19
            )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        assertEquals(10, filteredByActiveMembersResult.getResult().size());
        for(int i = 0; i < filteredByActiveMembersResult.getResult().size(); i++)
            assertEquals(19 - i, filteredByActiveMembersResult.getResult().get(i).getId());

        //filtered by active avg rating
        PagedSearchResult<List<Clan>> filteredByAvgRatingResult = objectMapper.readValue(mvc.perform
        (
            get("/api/clan/cursor/{cursor}/{maxCursor}/{maxCursor}/0/1"
                    + "?minAvgRating={min}&maxAvgRating={max}",
                cursor, max, max, 10, 19
            )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        assertEquals(10, filteredByAvgRatingResult.getResult().size());
        for(int i = 0; i < filteredByAvgRatingResult.getResult().size(); i++)
            assertEquals(18 - i, filteredByAvgRatingResult.getResult().get(i).getId());

        //filtered by all
        PagedSearchResult<List<Clan>> filteredByAllResult = objectMapper.readValue(mvc.perform
        (
            get("/api/clan/cursor/{cursor}/{maxCursor}/{maxCursor}/0/1"
                    + "?minActiveMembers={min}&maxActiveMembers={max}"
                    + "&minAvgRating={min}&maxAvgRating={max}",
                cursor, max, max, 10, 19, 10, 19
            )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        assertEquals(9, filteredByAllResult.getResult().size());
        for(int i = 0; i < filteredByAllResult.getResult().size(); i++)
            assertEquals(18 - i, filteredByAllResult.getResult().get(i).getId());
    }

}
