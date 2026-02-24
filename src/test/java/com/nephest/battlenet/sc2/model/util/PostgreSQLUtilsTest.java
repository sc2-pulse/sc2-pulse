// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Properties;
import org.junit.jupiter.api.Test;

public class PostgreSQLUtilsTest
{

    @Test
    public void testEscapeLike()
    {
        assertEquals("\\\\asd\\%\\\\\\%\\_", PostgreSQLUtils.escapeLikePattern("\\asd%\\%_"));
    }

    @Test
    public void testWithContainerAndDriverProperties()
    {
        Properties properties = new Properties();
        properties.put(PostgreSQLUtils.DRIVER_HOST, "localhost");
        properties.put(PostgreSQLUtils.DRIVER_PORT, 5432);
        properties.put(PostgreSQLUtils.CONTAINER_HOST, "172.17.0.2");
        properties.put(PostgreSQLUtils.CONTAINER_PORT, 9000);

        assertEquals("172.17.0.2", PostgreSQLUtils.getContainerHost(properties));
        assertEquals(9000, PostgreSQLUtils.getContainerPort(properties));
        assertEquals("172.17.0.2:9000", PostgreSQLUtils.getContainerHostAndPort(properties));
    }

    @Test
    public void testWithOnlyDriverProperties()
    {
        Properties properties = new Properties();
        properties.put(PostgreSQLUtils.DRIVER_HOST, "database.internal");
        properties.put(PostgreSQLUtils.DRIVER_PORT, 5432);

        assertEquals("database.internal", PostgreSQLUtils.getContainerHost(properties));
        assertEquals(5432, PostgreSQLUtils.getContainerPort(properties));
        assertEquals("database.internal:5432", PostgreSQLUtils.getContainerHostAndPort(properties));
    }

}
