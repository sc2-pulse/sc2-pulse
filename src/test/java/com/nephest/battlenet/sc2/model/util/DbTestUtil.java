// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.command.CommandResponse;
import com.nephest.battlenet.sc2.util.TestUtil;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

public class DbTestUtil
{

    public static void initDb(DataSource dataSource, Client clickHouseClient)
    throws Exception
    {
        clearDb(dataSource, clickHouseClient);
        CompletableFuture<CommandResponse> clickHouseInitTask
            = clickHouseClient.execute(TestUtil.readResource(
                DbTestUtil.class,
                "schema-clickhouse.sql"
        ));
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        finally
        {
            clickHouseInitTask.get().close();
        }
    }

    public static void clearDb(DataSource dataSource, Client clickHouseClient)
    throws Exception
    {
        CompletableFuture<CommandResponse> clickHouseClearTask
            = clickHouseClient.execute(TestUtil.readResource(
                DbTestUtil.class,
                "schema-drop-clickhouse.sql"
        ));
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
        finally
        {
            clickHouseClearTask.get().close();
        }
    }

    public static boolean isBatchUpdateValid(int rowsChanged, int expected)
    {
        return rowsChanged == expected || rowsChanged == Statement.SUCCESS_NO_INFO;
    }

    public static boolean isBatchUpdateValid(int rowsChanged)
    {
        return isBatchUpdateValid(rowsChanged, 1);
    }

    public static void assertBatchUpdate(int[] rowsChanged, int[] expected)
    {
        if(rowsChanged.length != expected.length)
            throw new IllegalArgumentException("Arrays must be of equal length");

        for(int i = 0; i < rowsChanged.length; i++)
        {
            int finalI = i;
            assertTrue
            (
                isBatchUpdateValid(rowsChanged[i], expected[i]),
                ()->"Invalid batch update: "
                    + "changed=" + Arrays.toString(rowsChanged) + ", "
                    + "expected=" + Arrays.toString(expected) + ", "
                    + "i=" + finalI
            );
        }
    }

    public static void assertBatchUpdate(int[] rowsChanged)
    {
        int[] expected = new int[rowsChanged.length];
        Arrays.fill(expected, 1);
        assertBatchUpdate(rowsChanged, expected);
    }

}
