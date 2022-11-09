// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.local.AuthenticationRequest;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.jdbc.JdbcTestUtils;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class AuthenticationRequestIT
{

    @Autowired
    private AuthenticationRequestDAO authenticationRequestDAO;

    @Autowired
    private JdbcTemplate template;

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

    @AfterEach
    public void afterEach(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }

    @Test
    public void testMerge()
    {
        OffsetDateTime odt = OffsetDateTime.now();
        AuthenticationRequest req = new AuthenticationRequest("1", odt);

        authenticationRequestDAO.merge(req);
        assertEquals(1, JdbcTestUtils.countRowsInTable(template, "authentication_request"));

        List<AuthenticationRequest> foundReqs = authenticationRequestDAO.find("1");
        assertEquals(1, foundReqs.size());
        AuthenticationRequest foundReq = foundReqs.get(0);
        assertEquals(req, foundReq);
        assertTrue(req.getCreated().isEqual(foundReq.getCreated()));

        assertTrue(authenticationRequestDAO.exists("1"));
        assertFalse(authenticationRequestDAO.exists("2"));
    }

    @Test
    public void testRemoveExpired()
    {
        OffsetDateTime minOdt = OffsetDateTime.now().minusSeconds(55);
        authenticationRequestDAO.merge(new AuthenticationRequest("1", minOdt));
        authenticationRequestDAO.merge(new AuthenticationRequest("2", minOdt.minusSeconds(10)));

        assertEquals(1, authenticationRequestDAO.removeExpired());
        assertTrue(authenticationRequestDAO.exists("1"));
        assertFalse(authenticationRequestDAO.exists("2"));
    }

    @Test
    public void testFindTtl()
    {
        Duration ttl = Duration.ofHours(1);
        //+5 to offset test time
        OffsetDateTime odt = OffsetDateTime.now().minus(ttl).plusSeconds(5);
        AuthenticationRequest req = new AuthenticationRequest("1", odt);
        authenticationRequestDAO.merge(req);

        assertTrue(authenticationRequestDAO.exists("1", ttl));
        assertFalse(authenticationRequestDAO.exists("1", ttl.minusSeconds(5)));
    }

}
