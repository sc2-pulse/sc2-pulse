// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.SortingOrder;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.navigation.Cursor;
import com.nephest.battlenet.sc2.model.navigation.NavigationDirection;
import com.nephest.battlenet.sc2.model.validation.CursorNavigableResult;
import com.nephest.battlenet.sc2.model.web.SortParameter;
import com.nephest.battlenet.sc2.web.service.WebServiceTestUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.ConversionService;
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
public class ClanSearchIT
{

    private static final int CLAN_COUNT = ClanDAO.PAGE_SIZE * 2;
    private static MockMvc mvc;
    private static Set<Clan> clans;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired @Qualifier("mvcConversionService")
    private ConversionService mvcConversionService;

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

            Region[] regions = Region.values();
            clans = IntStream.range(0, CLAN_COUNT)
                .boxed()
                .map(i->new Clan(
                    null,
                    i == 0 ? "c" : ("clan" + i),
                    regions[i % regions.length],
                    "name" + i))
                .collect(Collectors.toCollection(LinkedHashSet::new));
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
        Clan[] clans = WebServiceTestUtil
            .getObject(mvc, objectMapper, Clan[].class, "/api/clan/id/1,5,7");

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
        Clan[] clans = WebServiceTestUtil
            .getObject(mvc, objectMapper, Clan[].class, "/api/clan/tag/clan1");
        assertEquals(1, clans.length);
        Clan clan = clans[0];
        assertNotNull(clan);
        Assertions.assertThat(clan).usingRecursiveComparison().isEqualTo(new Clan(

            2, "clan1", Region.EU, "name1",
            5, 2, 3, null, 4
        ));
    }

    @Test
    public void testTagOrNameSearch()
    throws Exception
    {

        Clan[] clansByTag = WebServiceTestUtil
            .getObject(mvc, objectMapper, Clan[].class, "/api/clans?query=clan" + (CLAN_COUNT - 1));
        assertEquals(1, clansByTag.length);
        Clan clan = clansByTag[0];
        Assertions.assertThat(clan).usingRecursiveComparison().isEqualTo(new Clan(
            CLAN_COUNT, "clan" + (CLAN_COUNT - 1), Region.CN, "name" + (CLAN_COUNT - 1),
            103, 100, 101, null, 200
        ));


        Clan[] clansByName = WebServiceTestUtil
            .getObject(mvc, objectMapper, Clan[].class, "/api/clans?query=nAmE");
        assertEquals(CLAN_COUNT, clansByName.length);

        //LIKE search must be used only for names that are at least 3 characters long.
        Clan[] clansByShortName = WebServiceTestUtil
            .getObject(mvc, objectMapper, Clan[].class, "/api/clans?query=na");
        assertEquals(0, clansByShortName.length);
    }

    @Test
    public void testTagPrefixSearch()
    throws Exception
    {
        Clan[] clansByTag = objectMapper.readValue(mvc.perform
        (
            get("/api/clans")
                .queryParam("query", "cL")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), Clan[].class);
        Arrays.sort(clansByTag, Comparator.comparing(Clan::getTag));
        Clan[] expectedResult = clans.stream()
            .filter(c->c.getTag().startsWith("cl"))
            .sorted(Comparator.comparing(Clan::getTag))
            .toArray(Clan[]::new);
        Assertions.assertThat(expectedResult)
            .usingRecursiveComparison()
            .ignoringFields("members", "activeMembers", "games", "avgRating")
            .isEqualTo(clansByTag);
    }

    @Test
    public void whenSearchingByTagPrefixAndTermLengthIsLessThan2_thenSearchByTag()
    throws Exception
    {
        Clan[] clansByTag = objectMapper.readValue(mvc.perform
        (
            get("/api/clans")
                .queryParam("query", "C")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), Clan[].class);
        Assertions.assertThat(clansByTag)
            .usingRecursiveComparison()
            .ignoringFields("members", "activeMembers", "games", "avgRating")
            .isEqualTo(new Clan[]{clans.iterator().next()});
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
        CursorNavigableResult<List<Clan>> result = objectMapper.readValue(mvc.perform(
            get("/api/clans")
                .queryParam
                (
                    "after",
                    mvcConversionService.convert
                    (
                        new Cursor
                        (
                            ClanDAO.createCursorPosition(max + 1, max + 1),
                            NavigationDirection.FORWARD
                        ),
                        String.class
                    )
                )
                .queryParam
                (
                    "sort",
                    new SortParameter(cursor.getField(), SortingOrder.DESC).toPrefixedString()
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        assertEquals(ClanDAO.PAGE_SIZE, result.result().size());
        for(int i = 0; i < ClanDAO.PAGE_SIZE; i++)
            assertEquals(clanCount - i, result.result().get(i).getId());

        //reversed, last page
        CursorNavigableResult<List<Clan>> reversedResult = objectMapper.readValue(mvc.perform(
            get("/api/clans")
                .queryParam
                (
                    "before",
                    mvcConversionService.convert
                    (
                        new Cursor
                        (
                            ClanDAO.createCursorPosition(min - 1 , min - 1),
                            NavigationDirection.BACKWARD
                        ),
                        String.class
                    )
                )
                .queryParam
                (
                    "sort",
                    new SortParameter(cursor.getField(), SortingOrder.DESC).toPrefixedString()
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        assertEquals(ClanDAO.PAGE_SIZE, reversedResult.result().size());
        for(int i = 0; i < ClanDAO.PAGE_SIZE; i++)
            assertEquals(ClanDAO.PAGE_SIZE - i, reversedResult.result().get(i).getId());

        //filtered by active member count
        CursorNavigableResult<List<Clan>> filteredByActiveMembersResult = objectMapper.readValue(mvc.perform(
            get("/api/clans")
                .queryParam
                (
                    "after",
                    mvcConversionService.convert
                    (
                        new Cursor
                        (
                            ClanDAO.createCursorPosition(max, max),
                            NavigationDirection.FORWARD
                        ),
                        String.class
                    )
                )
                .queryParam
                (
                    "sort",
                    new SortParameter(cursor.getField(), SortingOrder.DESC).toPrefixedString()
                )
                .queryParam("activeMembersMin", "10")
                .queryParam("activeMembersMax", "19")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        assertEquals(10, filteredByActiveMembersResult.result().size());
        for(int i = 0; i < filteredByActiveMembersResult.result().size(); i++)
            assertEquals(19 - i, filteredByActiveMembersResult.result().get(i).getId());

        //filtered by active avg rating
        CursorNavigableResult<List<Clan>> filteredByAvgRatingResult = objectMapper.readValue(mvc.perform(
            get("/api/clans")
                .queryParam
                (
                    "after",
                    mvcConversionService.convert
                    (
                        new Cursor
                        (
                            ClanDAO.createCursorPosition(max, max),
                            NavigationDirection.FORWARD
                        ),
                        String.class
                    )
                )
                .queryParam
                (
                    "sort",
                    new SortParameter(cursor.getField(), SortingOrder.DESC).toPrefixedString()
                )
                .queryParam("avgRatingMin", "10")
                .queryParam("avgRatingMax", "19")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        assertEquals(10, filteredByAvgRatingResult.result().size());
        for(int i = 0; i < filteredByAvgRatingResult.result().size(); i++)
            assertEquals(18 - i, filteredByAvgRatingResult.result().get(i).getId());

        //filtered by region
        Region[] regions = Region.values();
        CursorNavigableResult<List<Clan>> filteredByRegion = objectMapper.readValue(mvc.perform(
            get("/api/clans")
                .queryParam
                (
                    "after",
                    mvcConversionService.convert
                    (
                        new Cursor
                        (
                            ClanDAO.createCursorPosition(max, max),
                            NavigationDirection.FORWARD
                        ),
                        String.class
                    )
                )
                .queryParam
                (
                    "sort",
                    new SortParameter(cursor.getField(), SortingOrder.DESC).toPrefixedString()
                )
                .queryParam("region", mvcConversionService.convert(Region.EU, String.class))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        assertEquals(CLAN_COUNT / regions.length, filteredByRegion.result().size());

        int euIx = Arrays.binarySearch(regions, Region.EU);
        int firstId = CLAN_COUNT - (regions.length - euIx) + 1;
        for(int i = 0; i < filteredByRegion.result().size(); i++)
            assertEquals(firstId - i * regions.length, filteredByRegion.result().get(i).getId());

        //filtered by all
        CursorNavigableResult<List<Clan>> filteredByAllResult = objectMapper.readValue(mvc.perform(
            get("/api/clans")
                .queryParam
                (
                    "after",
                    mvcConversionService.convert
                    (
                        new Cursor
                        (
                            ClanDAO.createCursorPosition(max, max),
                            NavigationDirection.FORWARD
                        ),
                        String.class
                    )
                )
                .queryParam
                (
                    "sort",
                    new SortParameter(cursor.getField(), SortingOrder.DESC).toPrefixedString()
                )
                .queryParam("activeMembersMin", "10")
                .queryParam("activeMembersMax", "19")
                .queryParam("avgRatingMin", "10")
                .queryParam("avgRatingMax", "19")
                .queryParam("region", mvcConversionService.convert(Region.EU, String.class))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        assertEquals(3, filteredByAllResult.result().size());
        int firstAllId = 18;
        for(int i = 0; i < filteredByAllResult.result().size(); i++)
            assertEquals(firstAllId - i * regions.length, filteredByAllResult.result().get(i).getId());
    }

    @Test
    public void testCursorNavigation()
    throws Exception
    {
        CursorNavigableResult<List<Clan>> firstResult = objectMapper.readValue(mvc.perform(
            get("/api/clans")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        assertEquals(ClanDAO.PAGE_SIZE, firstResult.result().size());
        for(int i = 0; i < ClanDAO.PAGE_SIZE; i++)
            assertEquals(CLAN_COUNT - i, firstResult.result().get(i).getId());

        CursorNavigableResult<List<Clan>> forwardResult = objectMapper.readValue(mvc.perform(
            get("/api/clans")
                .queryParam
                (
                    "after",
                    mvcConversionService.convert(firstResult.navigation().after(), String.class)
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        assertEquals(ClanDAO.PAGE_SIZE, forwardResult.result().size());
        for(int i = 0; i < ClanDAO.PAGE_SIZE; i++)
            assertEquals(ClanDAO.PAGE_SIZE - i, forwardResult.result().get(i).getId());

        CursorNavigableResult<List<Clan>> backwardResult = objectMapper.readValue(mvc.perform(
            get("/api/clans")
                .queryParam
                (
                    "before",
                    mvcConversionService.convert(forwardResult.navigation().before(), String.class)
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        assertEquals(ClanDAO.PAGE_SIZE, backwardResult.result().size());
        for(int i = 0; i < ClanDAO.PAGE_SIZE; i++)
            assertEquals(CLAN_COUNT - i, backwardResult.result().get(i).getId());

    }


}
