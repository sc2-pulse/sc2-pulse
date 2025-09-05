// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.model.navigation.Cursor;
import com.nephest.battlenet.sc2.model.navigation.NavigationDirection;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(classes = AllTestConfig.class)
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class CursorParameterRedirectFilterIT
{

    @Autowired
    private MockMvc mvc;

    @Autowired @Qualifier("mvcConversionService")
    private ConversionService mvcConversionService;

    @BeforeAll
    public static void beforeAll
    (
        @Autowired DataSource dataSource,
        @Autowired SeasonGenerator seasonGenerator
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        seasonGenerator.generateDefaultSeason(1);
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

    @ValueSource(strings = {"ladder", "following-ladder"})
    @ParameterizedTest
    public void testLadderParameters(String type)
    throws Exception
    {
        mvc.perform
        (
            get("/").queryParam("type", type)
                .queryParam("us", "true")
                .queryParam("eu", "true")
                .queryParam("kr", "true")
                .queryParam("cn", "true")

                .queryParam("bro", "true")
                .queryParam("sil", "true")
                .queryParam("gol", "true")
                .queryParam("pla", "true")
                .queryParam("dia", "true")
                .queryParam("mas", "true")
                .queryParam("gra", "true")

                .queryParam("otherParam", "otherVal")
                .queryParam("otherParam", "%")
                .queryParam("emptyParam", "")
                .contentType(MediaType.TEXT_HTML)
        )
            .andExpect(status().isMovedPermanently())
            .andExpect(header().string(
                "Location",
                Matchers.allOf(
                    Stream.of
                    (
                        Stream.of
                        (
                            Matchers.startsWith("http://localhost/?"),
                            Matchers.containsString("type=" + type),
                            Matchers.containsString("otherParam=otherVal"),
                            Matchers.containsString("otherParam=%25"),
                            Matchers.containsString("emptyParam")
                        ),
                        Arrays.stream(Region.values())
                            .map(r->"region=" + mvcConversionService.convert(r, String.class))
                            .map(Matchers::containsString),
                        Arrays.stream(BaseLeague.LeagueType.values())
                            .map(l->"league=" + mvcConversionService.convert(l, String.class))
                            .map(Matchers::containsString)
                    ).flatMap(Function.identity())
                        .collect(Collectors.toList())
                )))
            .andReturn();


    }

    @CsvSource
    ({
        "us",
        "bro"
    })
    @ParameterizedTest
    public void whenBooleanParameterIsNotTrue_thenRemoveIt(String parameterName)
    throws Exception
    {
        mvc.perform
        (
            get("/").queryParam("type", "ladder")
                .queryParam(parameterName, "false", "qwerty", "")
        )
            .andExpect(status().isMovedPermanently())
            .andExpect(header().string(
                "Location",
                Matchers.allOf(
                    Matchers.startsWith("http://localhost/?"),
                    Matchers.containsString("type=ladder"),
                    Matchers.not(Matchers.containsString(parameterName))
                )))
            .andReturn();
    }

    @CsvSource
    ({
        "1, FORWARD",
        "-1, BACKWARD"
    })
    @ParameterizedTest
    public void testLadderCursorRedirection
    (
        int count,
        NavigationDirection direction
    )
    throws Exception
    {
        Cursor cursor = new Cursor(LadderSearchDAO.createTeamCursorPosition(1L, 2L), direction);
        mvc.perform
        (
            get("/").queryParam("type", "ladder")
                .queryParam("ratingAnchor", "1")
                .queryParam("idAnchor", "2")
                .queryParam("count", String.valueOf(count))
                .contentType(MediaType.TEXT_HTML)
        )
            .andExpect(status().isMovedPermanently())
            .andExpect(header().string(
                "Location",
                Matchers.allOf(
                    Matchers.startsWith("http://localhost/?"),
                    Matchers.containsString("type=ladder"),
                    Matchers.containsString
                    (
                        direction.getRelativePosition() + "="
                            + mvcConversionService.convert(cursor, String.class)
                    ),
                    Matchers.not(Matchers.containsString("ratingAnchor")),
                    Matchers.not(Matchers.containsString("idAnchor")),
                    Matchers.not(Matchers.containsString("count"))
                )))
            .andReturn();
    }

    @CsvSource
    ({
        "ladder, count, 1, , , -rating",
        "ladder, count, 0, , , rating",
        "ladder, count, -1, , , rating",

        "clan-search, pageDiff, 1, , , -activeMembers",
        "clan-search, pageDiff, 0, , , activeMembers",
        "clan-search, pageDiff, -1, , , activeMembers",
        "clan-search, pageDiff, 1, sortBy, MEMBERS, -members"
    })
    @ParameterizedTest
    public void testPaginationRedirection
    (
        String type,
        String pageCountParameterName,
        int count,
        String sortByParameterName,
        String sortByParameterValue,
        String sort
    )
    throws Exception
    {
        MockHttpServletRequestBuilder builder = get("/")
            .queryParam("type", type)
            .queryParam("page", "1")
            .queryParam(pageCountParameterName, String.valueOf(count), null)
            .queryParam("otherParam", "otherVal")
            .contentType(MediaType.TEXT_HTML);
        if(sortByParameterName != null)
            builder = builder.queryParam(sortByParameterName, sortByParameterValue);
        List<Matcher<? super String>> matchers = new ArrayList<>(List.of(
            Matchers.startsWith("http://localhost/?"),
            Matchers.containsString("type=" + type),
            Matchers.not(Matchers.containsString("page=1")),
            Matchers.containsString("sort=" + sort),
            Matchers.containsString("otherParam=otherVal")
        ));
        if(sortByParameterName != null && !sortByParameterName.equals("sort"))
            matchers.add(Matchers.not(Matchers.containsString(sortByParameterName)));
        mvc.perform(builder)
            .andExpect(status().isMovedPermanently())
            .andExpect(header().string("Location", Matchers.allOf(matchers)))
            .andReturn();
    }

    @Test
    public void whenNoQueryParameters_thenOk()
    throws Exception
    {
        mvc.perform
        (
            get("/").queryParam("type", "ladder")
                .contentType(MediaType.TEXT_HTML)
        )
            .andExpect(status().isOk())
            .andReturn();
    }

    @Test
    public void whenNotLadderType_thenOk()
    throws Exception
    {
        mvc.perform
        (
            get("/").queryParam("type", "notLadder")
                .queryParam("idAnchor", "anchorValueAnchor")
                .contentType(MediaType.TEXT_HTML)
        )
            .andExpect(status().isOk())
            .andReturn();
    }

    @Test
    public void whenNotLadderPage_thenOk()
    throws Exception
    {
        mvc.perform
        (
            get("/status").queryParam("type", "ladder")
                .queryParam("idAnchor", "anchorValueAnchor")
                .contentType(MediaType.TEXT_HTML)
        )
            .andExpect(status().isOk())
            .andReturn();
    }

}
