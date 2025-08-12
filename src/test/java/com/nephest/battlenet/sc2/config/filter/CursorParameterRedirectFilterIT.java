// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.SortingOrder;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;
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
                .queryParam("idAnchor", "anchorValueAnchor")
                .queryParam("ratingAnchor", "1")
                .queryParam("ratingAnchor", "%")

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
                            Matchers.containsString("idCursor=anchorValueAnchor"),
                            Matchers.containsString("ratingCursor=1"),
                            Matchers.containsString("ratingCursor=%25"),
                            Matchers.containsString("otherParam=otherVal"),
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
        "ladder, count, 1, DESC",
        "ladder, count, 0, ASC",
        "ladder, count, -1, ASC",

        "clan-search, pageDiff, 1, DESC",
        "clan-search, pageDiff, 0, ASC",
        "clan-search, pageDiff, -1, ASC"
    })
    @ParameterizedTest
    public void testSortingOrder
    (
        String type,
        String pageCountParameterName,
        int count,
        SortingOrder sortingOrder
    )
    throws Exception
    {
        mvc.perform
        (
            get("/").queryParam("type", type)
                .queryParam(pageCountParameterName, String.valueOf(count), null)
                .queryParam("otherParam", "otherVal")
                .contentType(MediaType.TEXT_HTML)
        )
            .andExpect(status().isMovedPermanently())
            .andExpect(header().string(
                "Location",
                Matchers.allOf(
                    Matchers.startsWith("http://localhost/?"),
                    Matchers.containsString("type=" + type),
                    Matchers.containsString
                    (
                        "sortingOrder="
                        + mvcConversionService.convert(sortingOrder, String.class)
                    ),
                    Matchers.containsString("otherParam=otherVal")
                )))
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
