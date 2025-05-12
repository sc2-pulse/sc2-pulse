// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderPlayerSearchStats;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(classes = AllTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
@AutoConfigureMockMvc
public class LadderSearchIndependentEmptyIT
{

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static Account account;
    private static PlayerCharacter character;

    @BeforeAll
    public static void beforeAll
    (
        @Autowired DataSource dataSource,
        @Autowired AccountDAO accountDAO,
        @Autowired PlayerCharacterDAO playerCharacterDAO
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        account = accountDAO.merge(new Account(null, Partition.GLOBAL, "btag#1"));
        character = playerCharacterDAO.merge(new PlayerCharacter(
            null, account.getId(), Region.EU, 1L, 1, "name#1"));
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

    public static Stream<Arguments> testEmptyStats()
    {
        return Stream.of
        (
            Arguments.of
            (
                (Function<PlayerCharacter, MockHttpServletRequestBuilder>) playerCharacter->
                    get("/api/group/character/full")
                        .queryParam("characterId", String.valueOf(playerCharacter.getId()))
            ),
            Arguments.of
            (
                (Function<PlayerCharacter, MockHttpServletRequestBuilder>) playerCharacter->
                    get("/api/character/search")
                        .queryParam("term", playerCharacter.getDiscriminatedTag().tag())
            )
        );
    }

    @MethodSource
    @ParameterizedTest
    public void testEmptyStats(Function<PlayerCharacter, MockHttpServletRequestBuilder> builder)
    throws Exception
    {
        List<LadderDistinctCharacter> chars = objectMapper.readValue(mvc.perform(
            builder.apply(character)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(),
            new TypeReference<>(){}
        );
        assertEquals(1, chars.size());
        Assertions.assertThat(chars.get(0))
            .usingRecursiveComparison()
            .isEqualTo(new LadderDistinctCharacter(
                null, null,
                account,
                character,
                null, null, null, null, null,
                null, null, null, null, null,
                new LadderPlayerSearchStats(null, null, null),
                new LadderPlayerSearchStats(null, null, null)
            ));
    }

}
