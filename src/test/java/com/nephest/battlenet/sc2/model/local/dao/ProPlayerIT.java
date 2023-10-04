// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class ProPlayerIT
{

    @Autowired
    private ProPlayerDAO proPlayerDAO;

    @BeforeEach
    public void beforeEach(@Autowired DataSource dataSource)
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

    @Test
    public void whenNotExists_thenInsert()
    {
        proPlayerDAO.merge(new ProPlayer(null, 1L, "tag", "name"));
        proPlayerDAO.merge(new ProPlayer(null, 2L, "tag", "name"));

        assertEquals(2, proPlayerDAO.findAll().size());
    }


    @Test
    public void whenExists_thenUpdate()
    {
        proPlayerDAO.merge(new ProPlayer(null, 1L, "tag1", "name1"));
        proPlayerDAO.merge(new ProPlayer(null, 1L, "tag2", "name2"));
        ProPlayer lastPlayer = proPlayerDAO.merge(new ProPlayer(
            null,
            1L,
            "tag3",
            "name3",
            "US",
            LocalDate.now(),
            123,
            OffsetDateTime.now(),
            100 //supplied version doesn't matter
        ));

        List<ProPlayer> proPlayers = proPlayerDAO.findAll();
        assertEquals(1, proPlayers.size());
        ProPlayer proPlayer = proPlayers.get(0);
        assertEquals(lastPlayer.getNickname(), proPlayer.getNickname());
        assertEquals(lastPlayer.getName(), proPlayer.getName());
        assertEquals(lastPlayer.getCountry(), proPlayer.getCountry());
        assertEquals(lastPlayer.getBirthday(), proPlayer.getBirthday());
        assertEquals(lastPlayer.getEarnings(), proPlayer.getEarnings());
        assertTrue(lastPlayer.getUpdated().isEqual(proPlayer.getUpdated()));
        assertEquals(3, proPlayer.getVersion());
    }

    @Test
    public void whenExistsWithSameData_thenDontUpdateVersion()
    {
        ProPlayer lastPlayer = proPlayerDAO.merge(new ProPlayer(
            null,
            1L,
            "tag3",
            "name3",
            "US",
            LocalDate.now(),
            123,
            OffsetDateTime.now(),
            1
        ));
        proPlayerDAO.merge(lastPlayer);

        List<ProPlayer> proPlayers = proPlayerDAO.findAll();
        assertEquals(1, proPlayers.size());
        ProPlayer proPlayer = proPlayers.get(0);
        assertEquals(lastPlayer.getVersion(), proPlayer.getVersion());
    }

    @Test
    public void whenMergingWithCorrectVersion_thenUpdate()
    {
        LocalDate birthday1 = LocalDate.now();
        ProPlayer proPlayer = proPlayerDAO.mergeVersioned(new ProPlayer(
            null,
            null,
            "tag1",
            "name1",
            "US",
            birthday1,
            123,
            OffsetDateTime.now(),
            1
        ));
        ProPlayer updatedProPlayer = proPlayerDAO.mergeVersioned(new ProPlayer(
            proPlayer.getId(),
            null,
            "tag2",
            "name2",
            "EU",
            birthday1.minusDays(1),
            456,
            OffsetDateTime.now(),
            1
        ));
        assertEquals(2, updatedProPlayer.getVersion());

        ProPlayer foundPlayer = proPlayerDAO.findAll().get(0);
        Assertions.assertThat(foundPlayer)
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .isEqualTo(updatedProPlayer);
    }

    @Test
    public void whenMergingWithCorrectVersionButOnlyDifferenceIsUpdateTimestamp_thenDontUpdateVersion()
    {
        LocalDate birthday1 = LocalDate.now();
        OffsetDateTime odt1 = OffsetDateTime.now();
        ProPlayer proPlayer = proPlayerDAO.mergeVersioned(new ProPlayer(
            null,
            null,
            "tag1",
            "name1",
            "US",
            birthday1,
            123,
            odt1,
            1
        ));
        ProPlayer updatedProPlayer = proPlayerDAO.mergeVersioned(new ProPlayer(
            proPlayer.getId(),
            null,
            "tag1",
            "name1",
            "US",
            birthday1,
            123,
            odt1.plusSeconds(1),
            1
        ));
        assertEquals(1, updatedProPlayer.getVersion());

        ProPlayer foundPlayer = proPlayerDAO.findAll().get(0);
        Assertions.assertThat(foundPlayer)
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .isEqualTo(updatedProPlayer);
    }

    @Test
    public void whenMergingWithWrongVersion_thenThrowException()
    {
        LocalDate birthday1 = LocalDate.now();
        ProPlayer proPlayer = proPlayerDAO.mergeVersioned(new ProPlayer(
            null,
            null,
            "tag1",
            "name1",
            "US",
            birthday1,
            123,
            OffsetDateTime.now(),
            1
        ));
        ProPlayer invalidVersionPlayer = new ProPlayer
        (
            proPlayer.getId(),
            null,
            "tag2",
            "name2",
            "EU",
            birthday1.minusDays(1),
            456,
            OffsetDateTime.now(),
            2
        );
        assertThrows
        (
            OptimisticLockingFailureException.class,
            ()->proPlayerDAO.mergeVersioned(invalidVersionPlayer)
        );
    }

    @Test
    public void testFindByIds()
    {
        ProPlayer proPlayer1 = proPlayerDAO.mergeVersioned(new ProPlayer(
            null,
            null,
            "tag1",
            "name1",
            "US",
            LocalDate.now(),
            123,
            OffsetDateTime.now(),
            1
        ));
        ProPlayer proPlayer2 = proPlayerDAO.mergeVersioned(new ProPlayer(
            null,
            null,
            "tag2",
            "name2",
            "US",
            LocalDate.now(),
            123,
            OffsetDateTime.now(),
            1
        ));
        ProPlayer proPlayer3 = proPlayerDAO.mergeVersioned(new ProPlayer(
            null,
            null,
            "tag3",
            "name3",
            "US",
            LocalDate.now(),
            123,
            OffsetDateTime.now(),
            1
        ));
        List<ProPlayer> proPlayers = proPlayerDAO
            .find(Set.of(proPlayer1.getId(), proPlayer3.getId()));
        assertEquals(2, proPlayers.size());
        Assertions.assertThat(proPlayers.get(0))
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .isEqualTo(proPlayer1);
        Assertions.assertThat(proPlayers.get(1))
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .isEqualTo(proPlayer3);
    }

}
