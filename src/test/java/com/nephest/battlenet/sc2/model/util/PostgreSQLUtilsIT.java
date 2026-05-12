// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.extension.AutoConfigureDatabase;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@AutoConfigureDatabase
public class PostgreSQLUtilsIT
{

    @Autowired
    private PostgreSQLUtils postgreSQLUtils;

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
