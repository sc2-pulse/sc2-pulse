// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.BasePlayerCharacter;
import com.nephest.battlenet.sc2.model.IdField;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

@SpringBootTest(classes = {AllTestConfig.class})
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class CharacterIdSearchIT
{

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private PlayerCharacterDAO playerCharacterDAO;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired @Qualifier("mvcConversionService")
    private ConversionService mvcConversionService;

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

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    public void testFindByName(boolean caseSensitive)
    throws Exception
    {
        Account acc = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        PlayerCharacter[] chars = new PlayerCharacter[]
        {
            new PlayerCharacter(null, acc.getId(), Region.EU, 1L, 1, "name1#1"),
            new PlayerCharacter(null, acc.getId(), Region.EU, 2L, 1, "name1#1"),
            new PlayerCharacter(null, acc.getId(), Region.EU, 3L, 1, "namE1#1"),
            new PlayerCharacter(null, acc.getId(), Region.EU, 4L, 1, "name2#1")
        };
        Arrays.stream(chars).forEach(playerCharacterDAO::merge);
        Long[] ids = objectMapper.readValue(mvc.perform
        (
            get("/api/characters")
                .queryParam("field", mvcConversionService.convert(IdField.ID, String.class))
                .queryParam("name", "name1")
                .queryParam("caseSensitive", String.valueOf(caseSensitive))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), Long[].class);
        Long[] expectedIds = IntStream.range(0, caseSensitive ? 2 : 3)
            .boxed()
            .map(i->chars[i])
            .map(PlayerCharacter::getId)
            .toArray(Long[]::new);
        Arrays.sort(ids);
        assertArrayEquals(expectedIds, ids);
    }

    @Test
    public void testFindByNameAndRegions()
    throws Exception
    {
        Account acc = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        PlayerCharacter[] chars = new PlayerCharacter[]
        {
            new PlayerCharacter(null, acc.getId(), Region.EU, 1L, 1, "name1#1"),
            new PlayerCharacter(null, acc.getId(), Region.US, 2L, 1, "name1#1"),
            new PlayerCharacter(null, acc.getId(), Region.KR, 3L, 1, "name1#1"),
            new PlayerCharacter(null, acc.getId(), Region.EU, 4L, 1, "name2#1"),
            new PlayerCharacter(null, acc.getId(), Region.KR, 5L, 1, "name3#1"),
        };
        Arrays.stream(chars).forEach(playerCharacterDAO::merge);
        Long[] ids = objectMapper.readValue(mvc.perform
        (
            get("/api/characters")
                .queryParam("field", mvcConversionService.convert(IdField.ID, String.class))
                .queryParam("name", "name1")
                .queryParam
                (
                    "region",
                    mvcConversionService.convert(Region.KR, String.class)
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), Long[].class);
        assertArrayEquals(new Long[]{chars[2].getId()}, ids);
    }

    @Test
    public void testFindByNameAndSeason()
    throws Exception
    {
        seasonGenerator.generateSeason
        (
            List.of
            (
                new Season(null, 1, Region.EU, 2020, 1,
                    SC2Pulse.offsetDateTime(2020, 1, 1), SC2Pulse.offsetDateTime(2020, 2, 1)),
                new Season(null, 2, Region.EU, 2020, 2,
                    SC2Pulse.offsetDateTime(2020, 2, 1), SC2Pulse.offsetDateTime(2020, 3, 1))
            ),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            1
        );
        Long[] ids = objectMapper.readValue(mvc.perform
        (
            get("/api/characters")
                .queryParam("field", mvcConversionService.convert(IdField.ID, String.class))
                .queryParam("name", "character")
                .queryParam("season", "1")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), Long[].class);
        assertArrayEquals(new Long[]{1L}, ids);
    }

    @Test
    public void testFindByNameAndQueue()
    throws Exception
    {
        seasonGenerator.generateSeason
        (
            List.of
            (
                new Season(null, 1, Region.EU, 2020, 1,
                    SC2Pulse.offsetDateTime(2020, 1, 1), SC2Pulse.offsetDateTime(2020, 2, 1)),
                new Season(null, 2, Region.EU, 2020, 2,
                    SC2Pulse.offsetDateTime(2020, 2, 1), SC2Pulse.offsetDateTime(2020, 3, 1))
            ),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_1V1, QueueType.LOTV_2V2, QueueType.LOTV_3V3),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            1
        );
        Long[] ids = objectMapper.readValue(mvc.perform
        (
            get("/api/characters")
                .queryParam("field", mvcConversionService.convert(IdField.ID, String.class))
                .queryParam("name", "character")
                .queryParam
                (
                    "queue",
                    mvcConversionService.convert(QueueType.LOTV_1V1, String.class),
                    mvcConversionService.convert(QueueType.LOTV_2V2, String.class)
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), Long[].class);
        Arrays.sort(ids);
        assertArrayEquals
        (
            new Long[]
            {
                1L, //s1 1v1
                2L, 3L, //s1 2v2
                7L, //s2 1v1
                8L, 9L //s2 2v2
            },
            ids
        );
    }

    @Test
    public void whenFakeName_thenBadRequest()
    throws Exception
    {
        mvc.perform
        (
            get("/api/characters")
                .queryParam("field", mvcConversionService.convert(IdField.ID, String.class))
                .contentType(MediaType.APPLICATION_JSON)
                .queryParam("name", BasePlayerCharacter.DEFAULT_FAKE_FULL_NAME)
        )
            .andExpect(status().isBadRequest());
    }

    @Test
    public void whenMultipleSeasonsAndQueues_thenBadRequest()
    throws Exception
    {
        mvc.perform
        (
            get("/api/characters")
                .queryParam("field", mvcConversionService.convert(IdField.ID, String.class))
                .contentType(MediaType.APPLICATION_JSON)
                .queryParam("name", "name")
                .queryParam("season", "1", "2")
                .queryParam
                (
                    "queue",
                    mvcConversionService.convert(QueueType.LOTV_1V1, String.class),
                    mvcConversionService.convert(QueueType.LOTV_2V2, String.class)
                )
        )
            .andExpect(status().isBadRequest());
    }

}
