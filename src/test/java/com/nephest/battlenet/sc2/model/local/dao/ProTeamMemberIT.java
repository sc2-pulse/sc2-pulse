// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.ProTeam;
import com.nephest.battlenet.sc2.model.local.ProTeamMember;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class ProTeamMemberIT
{

    @Autowired
    private ProPlayerDAO proPlayerDAO;

    @Autowired
    private ProTeamDAO proTeamDAO;

    @Autowired
    private ProTeamMemberDAO proTeamMemberDAO;

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
    public void testFindByIds()
    {
        ProPlayer proPlayer1 = proPlayerDAO.merge(new ProPlayer(null, 1L, "tag1", "name1"));
        ProPlayer proPlayer2 = proPlayerDAO.merge(new ProPlayer(null, 2L, "tag2", "name2"));
        ProPlayer proPlayer3 = proPlayerDAO.merge(new ProPlayer(null, 3L, "tag3", "name3"));

        ProTeam team1 = proTeamDAO.merge(new ProTeam(null, 1L, "name1", "sn1"));
        ProTeam team2 = proTeamDAO.merge(new ProTeam(null, 2L, "name2", "sn2"));
        ProTeam team3 = proTeamDAO.merge(new ProTeam(null, 3L, "name3", "sn3"));

        ProTeamMember[] members = new ProTeamMember[]
        {
            new ProTeamMember(team1.getId(), proPlayer1.getId()),
            new ProTeamMember(team2.getId(), proPlayer2.getId()),
            new ProTeamMember(team3.getId(), proPlayer3.getId())
        };
        proTeamMemberDAO.merge(members);

        List<ProTeamMember> foundMembers = proTeamMemberDAO
            .findByProPlayerIds(proPlayer1.getId(), proPlayer3.getId());
        assertEquals(2, foundMembers.size());
        foundMembers.sort(Comparator.comparing(ProTeamMember::getProPlayerId));
        Assertions.assertThat(foundMembers.get(0))
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .isEqualTo(members[0]);
        Assertions.assertThat(foundMembers.get(1))
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .isEqualTo(members[2]);
    }

}
