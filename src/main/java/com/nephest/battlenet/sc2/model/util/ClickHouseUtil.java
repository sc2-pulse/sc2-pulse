// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.util;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.data_formats.ClickHouseBinaryFormatReader;
import com.clickhouse.client.api.query.QueryResponse;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class ClickHouseUtil
{

    private static final byte[] EMPTY_JSON_ARRAY = "[]".getBytes(StandardCharsets.UTF_8);

    private final Client clickHouseClient;

    private static final String GET_COUNT = "SELECT COUNT(*) AS count FROM %1$s";

    @Autowired
    public ClickHouseUtil(Client clickHouseClient)
    {
        this.clickHouseClient = clickHouseClient;
    }

    public static String quote(String s)
    {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    public static String toInClause(List<String> values)
    {
        return values.stream()
            .map(ClickHouseUtil::quote)
            .collect(Collectors.joining(",", "(", ")"));
    }

    public static InputStream createEmptyJsonArrayInputStream()
    {
        return new ByteArrayInputStream(EMPTY_JSON_ARRAY);
    }

    /**
     * Use with care, parameters are passed in raw form, SQL injection is possible.
     *
     * @param tableName table name
     * @return row count
     */
    public long getCount(String tableName)
    {
        try
        (
            QueryResponse response = clickHouseClient
                .query(GET_COUNT.formatted(tableName))
                .get();
            ClickHouseBinaryFormatReader reader = clickHouseClient.newBinaryFormatReader(response)
        )
        {
            reader.next();
            return reader.getLong("count");
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

}
