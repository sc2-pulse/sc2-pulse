// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.LadderUpdate;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class LadderUpdateDAOIT
{

    @Autowired
    private LadderUpdateDAO ladderUpdateDAO;

    @Autowired
    private JdbcTemplate template;

    @BeforeAll
    public static void beforeAll
    (
        @Autowired @Qualifier("dataSource") DataSource dataSource,
        @Autowired LadderUpdateDAO ladderUpdateDAO
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
    }

    @AfterEach
    public void afterEach()
    throws SQLException
    {
        template.update("DELETE FROM ladder_update");
    }

    @AfterAll
    public static void afterAll
    (
        @Autowired @Qualifier("dataSource") DataSource dataSource
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }

    @Test
    public void testRemoveExpired()
    {
        OffsetDateTime odt = SC2Pulse.offsetDateTime().minusDays(30).plusSeconds(10);
        List<LadderUpdate> original = List.of
        (
            new LadderUpdate
            (
                Region.EU,
                QueueType.LOTV_1V1,
                BaseLeague.LeagueType.BRONZE,
                odt.minusSeconds(11),
                Duration.ofSeconds(1)
            ),
            new LadderUpdate
            (
                Region.US,
                QueueType.LOTV_2V2,
                BaseLeague.LeagueType.SILVER,
                odt,
                Duration.ofSeconds(2)
            ),
            new LadderUpdate
            (
                Region.KR,
                QueueType.LOTV_3V3,
                BaseLeague.LeagueType.GOLD,
                odt.minusSeconds(12),
                Duration.ofSeconds(3)
            )
        );
        ladderUpdateDAO.create(Set.copyOf(original));
        List<LadderUpdate> found = ladderUpdateDAO.getAll();
        found.sort(Comparator.comparing(LadderUpdate::getDuration));
        Assertions.assertThat(found)
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .isEqualTo(original);

        assertEquals(2, ladderUpdateDAO.removeExpired());
        Assertions.assertThat(ladderUpdateDAO.getAll())
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .isEqualTo(List.of(original.get(1)));
    }

}
