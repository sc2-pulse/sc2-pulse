// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class PostgreSQLUtilsIT
{

    @Autowired
    private PostgreSQLUtils postgreSQLUtils;

    @BeforeEach
    public void beforeAll(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
    }

    @AfterEach
    public void afterAll(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    public void testReindex(boolean concurrently)
    {
        postgreSQLUtils.reindex(Set.of("ix_match_updated"), concurrently);
    }

    @CsvSource({"'1'", "'asd'", ","})
    @ParameterizedTest
    public void whenSettingTransactionUserId_thenUseSessionVariables(String id)
    {
        assertEquals(id, postgreSQLUtils.setTransactionUserId(id));
        //no transaction
        assertNull(postgreSQLUtils.getTransactionUserId());
    }

    @Transactional
    @CsvSource({"'1'", "'asd'", ","})
    @ParameterizedTest
    public void testSetTransactionUserId(String id)
    {
        assertEquals(id, postgreSQLUtils.setTransactionUserId(id));
        assertEquals(id, postgreSQLUtils.getTransactionUserId());
    }



}
