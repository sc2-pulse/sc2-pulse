// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static com.nephest.battlenet.sc2.model.Race.PROTOSS;
import static com.nephest.battlenet.sc2.model.Race.RANDOM;
import static com.nephest.battlenet.sc2.model.Race.TERRAN;
import static com.nephest.battlenet.sc2.model.Race.ZERG;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.local.MapStatsFilmSpec;
import com.nephest.battlenet.sc2.model.local.MatchUp;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class MapStatsFilmSpecDAOIT
{

    @Autowired
    private MapStatsFilmSpecDAO mapStatsFilmSpecDAO;

    @Autowired
    private JdbcTemplate template;

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

    @AfterEach
    public void afterEach()
    {
        template.update("DELETE FROM map_stats_film_spec");
    }

    @Test
    public void testCreate()
    {
        MapStatsFilmSpec spec = new MapStatsFilmSpec(null, TERRAN, PROTOSS,
            Duration.ofSeconds(10));
        mapStatsFilmSpecDAO.create(spec);
        assertNotNull(spec.getId());
    }

    @Test
    public void testFindBySpec()
    {
        MapStatsFilmSpec spec1 = new MapStatsFilmSpec(null, TERRAN, PROTOSS,
            Duration.ofSeconds(10));
        MapStatsFilmSpec spec2 = new MapStatsFilmSpec(null, TERRAN, ZERG,
            Duration.ofSeconds(10));
        MapStatsFilmSpec spec3 = new MapStatsFilmSpec(null, TERRAN, RANDOM,
            Duration.ofSeconds(10));
        MapStatsFilmSpec spec4 = new MapStatsFilmSpec(null, TERRAN, PROTOSS,
            Duration.ofSeconds(20));
        mapStatsFilmSpecDAO.create(spec1);
        mapStatsFilmSpecDAO.create(spec2);
        mapStatsFilmSpecDAO.create(spec3);
        mapStatsFilmSpecDAO.create(spec4);
        List<MapStatsFilmSpec> found = mapStatsFilmSpecDAO.find
        (
            Set.of
            (
                new MatchUp(TERRAN, PROTOSS),
                new MatchUp(TERRAN, RANDOM)
            ),
            Duration.ofSeconds(10)
        );
        found.sort(Comparator.comparing(MapStatsFilmSpec::getId));
        Assertions.assertThat(found)
            .usingRecursiveComparison()
            .isEqualTo(List.of(spec1, spec3));
    }

}
