// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.clickhouse.client.api.Client;
import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.util.DbTestUtil;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
public class PostgreSQLUtilsIT
{

    @Autowired
    private PostgreSQLUtils postgreSQLUtils;

    @BeforeEach
    public void beforeAll(@Autowired DataSource dataSource, @Autowired Client clickHouseClient)
    throws Exception
    {
        DbTestUtil.initDb(dataSource, clickHouseClient);
    }

    @AfterEach
    public void afterAll(@Autowired DataSource dataSource, @Autowired Client clickHouseClient)
    throws Exception
    {
        DbTestUtil.clearDb(dataSource, clickHouseClient);
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
