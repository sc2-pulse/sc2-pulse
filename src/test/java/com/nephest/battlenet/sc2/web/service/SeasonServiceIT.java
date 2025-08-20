// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.SpyBeanConfig;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.web.SeasonService;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = {AllTestConfig.class, SpyBeanConfig.class})
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class SeasonServiceIT
{

    @Autowired
    private SeasonService seasonService;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SeasonDAO seasonDAO;

    @BeforeEach
    public void beforeEach(@Autowired DataSource dataSource)
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

    @Test
    public void whenNewSeasonIsInsertedOrUpdated_thenSetPreviousSeasonEndDateToNewStartDate()
    throws Exception
    {
        OffsetDateTime start = SC2Pulse.offsetDateTime().minusYears(1);

        //insert
        Season season1 = seasonService.merge(new Season(null, 1, Region.US, 2020, 1,
            start, start.plusHours(10)));
        Season season2 = seasonService.merge(new Season(null, 1, Region.EU, 2020, 1,
            start, start.plusHours(10)));
        Season season3 = seasonService.merge(new Season(null, 2, Region.EU, 2020, 2,
            start.plusHours(11), start.plusHours(20)));
        season2.setEnd(season3.getStart());
        Assertions.assertThat(getAllSeasons())
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .isEqualTo(List.of(season3, season2, season1));

        //update
        season3.setStart(season3.getStart().plusHours(1));
        seasonService.merge(season3);
        season2.setEnd(season3.getStart());
        Assertions.assertThat(getAllSeasons())
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .isEqualTo(List.of(season3, season2, season1));
    }

    @Test
    public void whenNewSeasonIsInsertedOrUpdated_thenSetNewEndDateToNextSeasonStartDate()
    throws Exception
    {
        OffsetDateTime start = SC2Pulse.offsetDateTime().minusYears(1);

        //insert
        Season season1 = seasonService.merge(new Season(null, 1, Region.US, 2020, 1,
            start, start.plusHours(10)));
        Season season3 = seasonService.merge(new Season(null, 2, Region.EU, 2020, 2,
            start.plusHours(11), start.plusHours(20)));
        Season season2 = seasonService.merge(new Season(null, 1, Region.EU, 2020, 1,
            start, season3.getStart().minusHours(1)));
        season2.setEnd(season3.getStart());
        Assertions.assertThat(getAllSeasons())
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .isEqualTo(List.of(season3, season2, season1));

        //update
        season2.setEnd(season3.getStart().minusHours(2));
        seasonService.merge(season2);
        season2.setEnd(season3.getStart());
        Assertions.assertThat(getAllSeasons())
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .isEqualTo(List.of(season3, season2, season1));
    }

    @Test
    public void whenExceptionIsThrownBeforeCurrentSeasonIsSaved_thenPreviousSeasonChangeIsReverted()
    throws Exception
    {
        OffsetDateTime start = SC2Pulse.offsetDateTime().minusYears(1);
        Season season2 = seasonService.merge(new Season(null, 1, Region.EU, 2020, 1,
            start, start.plusHours(10)));

        doThrow(new RuntimeException("test"))
            .when(seasonDAO)
            .merge(argThat(s->s.getBattlenetId() == 2));
        assertThrows
        (
            RuntimeException.class,
            ()->seasonService.merge(new Season(null, 2, Region.EU, 2020, 2,
                start.plusHours(11), start.plusHours(20))),
            "test"
        );
        //2 times: insert, update end date
        verify(seasonDAO, times(2)).merge(argThat(s->s.getBattlenetId() == 1));
        //season1 has not changed
        Assertions.assertThat(getAllSeasons())
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .isEqualTo(List.of(season2));
    }

    private List<Season> getAllSeasons()
    throws Exception
    {
        return objectMapper.readValue(mvc.perform
        (
            get("/api/seasons")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
    }

}
