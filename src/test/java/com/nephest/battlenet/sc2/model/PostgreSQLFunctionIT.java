// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class PostgreSQLFunctionIT
{

    @Autowired
    private JdbcTemplate template;

    @Autowired @Qualifier("sc2StatsConversionService")
    private ConversionService conversionService;

    @BeforeAll
    public static void beforeAll(@Autowired DataSource dataSource)
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

    @CsvSource
    ({
        "200, 100000, true, 0, GRANDMASTER, FIRST",
        "200, 100000, false, 1, MASTER, FIRST",
        
        "1332, 100000, false, 1, MASTER, FIRST",
        "2665, 100000, true, 2, MASTER, SECOND",
        "4000, 100000, true, 3, MASTER, THIRD",

        "11665, 100000, true, 4, DIAMOND, FIRST",
        "19332, 100000, true, 5, DIAMOND, SECOND",
        "27000, 100000, true, 6, DIAMOND, THIRD",

        "34665, 100000, true, 7, PLATINUM, FIRST",
        "42332, 100000, true, 8, PLATINUM, SECOND",
        "50000, 100000, true, 9, PLATINUM, THIRD",

        "57665, 100000, true, 10, GOLD, FIRST",
        "65332, 100000, true, 11, GOLD, SECOND",
        "73000, 100000, true, 12, GOLD, THIRD",

        "80665, 100000, true, 13, SILVER, FIRST",
        "88332, 100000, true, 14, SILVER, SECOND",
        "96000, 100000, true, 15, SILVER, THIRD",

        "97332, 100000, true, 16, BRONZE, FIRST",
        "98665, 100000, true, 17, BRONZE, SECOND",
        "100000, 100000, true, 18, BRONZE, THIRD",

        "100010, 100000, true, 18, BRONZE, THIRD",
    })
    @ParameterizedTest
    public void test_get_top_percentage_league_tier_lotv
    (
        int rank, double teamCount, boolean gm,
        int id, BaseLeague.LeagueType league, BaseLeagueTier.LeagueTierType tier
    )
    {
        RowMapper<Integer[]> rm = (rs, i)->new Integer[]{
            rs.getInt(1),
            rs.getInt(2),
            rs.getInt(3)
        };
        Integer[] data = template.query
        (
            "SELECT * FROM get_top_percentage_league_tier_lotv(?, ?, ?)",
            rm,
            rank, teamCount, gm
        ).get(0);
        assertEquals(id, data[0]);
        assertEquals(league, conversionService.convert(data[1], BaseLeague.LeagueType.class));
        assertEquals(tier, conversionService.convert(data[2], BaseLeagueTier.LeagueTierType.class));
    }

}