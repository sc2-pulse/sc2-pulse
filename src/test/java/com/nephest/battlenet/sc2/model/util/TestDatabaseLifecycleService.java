// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.util;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.command.CommandResponse;
import com.nephest.battlenet.sc2.util.TestUtil;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;

@Service
public class TestDatabaseLifecycleService
{

    private final DataSource dataSource;
    private final Client clickHouseClient;

    @Autowired
    public TestDatabaseLifecycleService(DataSource dataSource, Client clickHouseClient)
    {
        this.dataSource = dataSource;
        this.clickHouseClient = clickHouseClient;
    }

    public void initDb()
    {
        try
        {
            clearDb();
            CompletableFuture<CommandResponse> clickHouseInitTask = clickHouseClient.execute
            (
                TestUtil.readResource
                (
                    TestDatabaseLifecycleService.class,
                    "schema-clickhouse.sql"
                )
            );
            try(var connection = dataSource.getConnection())
            {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
            }
            finally
            {
                clickHouseInitTask.get().close();
            }
        }
        catch(Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public void clearDb()
    {
        try
        {
            CompletableFuture<CommandResponse> clickHouseClearTask = clickHouseClient.execute
            (
                TestUtil.readResource
                (
                    TestDatabaseLifecycleService.class,
                    "schema-drop-clickhouse.sql"
                )
            );
            try(var connection = dataSource.getConnection())
            {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            }
            finally
            {
                clickHouseClearTask.get().close();
            }
        }
        catch(Exception e)
        {
            throw new RuntimeException(e);
        }
    }

}
