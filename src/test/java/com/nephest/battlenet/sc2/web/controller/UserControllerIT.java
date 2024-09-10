// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.AccountRoleDAO;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
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
public class UserControllerIT
{

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("mvcConversionService")
    private ConversionService conversionService;

    private static Account[] accounts;

    @BeforeAll
    public static void beforeAll
    (
        @Autowired DataSource dataSource,
        @Autowired SeasonGenerator seasonGenerator,
        @Autowired AccountRoleDAO accountRoleDAO
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        accounts = seasonGenerator.generateAccounts(Partition.GLOBAL, "tag", 10);
        accountRoleDAO.addRoles
        (
            1L,
            EnumSet.of(SC2PulseAuthority.MODERATOR, SC2PulseAuthority.REVEALER)
        );
        accountRoleDAO.addRoles(2L, EnumSet.of(SC2PulseAuthority.MODERATOR));
        accountRoleDAO.addRoles(3L, EnumSet.of(SC2PulseAuthority.REVEALER));
        accountRoleDAO.addRoles(4L, EnumSet.of(SC2PulseAuthority.ADMIN));
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

    public static Stream<Arguments> testGetByRole()
    {
        return Stream.of
        (
            Arguments.of(SC2PulseAuthority.MODERATOR, new Account[]{accounts[0], accounts[1]}),
            Arguments.of(SC2PulseAuthority.REVEALER, new Account[]{accounts[0], accounts[2]})
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testGetByRole(SC2PulseAuthority role, Account[] accounts)
    throws Exception
    {
        Account[] foundAccounts = objectMapper.readValue(mvc.perform
        (
            get
            (
                "/api/user/role/{role}",
                conversionService.convert(role, String.class)
            )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), Account[].class);
        Arrays.sort(foundAccounts, Account.NATURAL_ID_COMPARATOR);
        Arrays.sort(accounts, Account.NATURAL_ID_COMPARATOR);
        Assertions.assertThat(foundAccounts)
            .usingRecursiveComparison()
            .isEqualTo(accounts);
    }

    @ParameterizedTest
    @EnumSource
    (
        value = SC2PulseAuthority.class,
        mode = EnumSource.Mode.EXCLUDE,
        names = {"MODERATOR", "REVEALER"}
    )
    public void ifNotSupportedRoleThenBadRequest(SC2PulseAuthority role)
    throws Exception
    {
        mvc.perform
        (
            get
            (
                "/api/user/role/{role}",
                conversionService.convert(role, String.class)
            )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isBadRequest());
    }


}
