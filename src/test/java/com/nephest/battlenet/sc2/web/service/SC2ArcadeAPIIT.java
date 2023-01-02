// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardFullPlayerCharacter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class SC2ArcadeAPIIT
{

    @Autowired
    private SC2ArcadeAPI api;

    private static WebClient originalClient;

    @BeforeAll
    public static void beforeAll(@Autowired SC2ArcadeAPI api, @Autowired DataSource dataSource)
    throws SQLException
    {
        originalClient = WebServiceTestUtil.fastTimers(api);
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
    }

    @AfterAll
    public static void afterAll(@Autowired SC2ArcadeAPI api, @Autowired DataSource dataSource)
    throws SQLException
    {
        WebServiceTestUtil.revertFastTimers(api, originalClient);
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }

    @Test
    public void testFindByRegionAndGameId()
    {
        BlizzardFullPlayerCharacter character =
            api.findByRegionAndGameId(Region.EU, "78294784").block();
        assertNotNull(character);
        assertEquals(Region.EU, character.getRegion());
        assertEquals(1, character.getRealm());
        assertEquals(2642502, character.getId());
        assertEquals("Talv#1", character.getName());
    }

    @Test
    public void testRetrying()
    throws IOException
    {
        MockWebServer server = new MockWebServer();
        server.start();
        WebClient oldWebClient = api.getWebClient();
        WebClient timeoutClient = WebServiceTestUtil.createTimeoutClient().mutate()
            .baseUrl(server.url("/someurl").uri().toString())
            .build();
        api.setWebClient(timeoutClient);

        String validCharacter = "{\n"
            + "  \"regionId\": 2,\n"
            + "  \"realmId\": 1,\n"
            + "  \"profileId\": 2642502,\n"
            + "  \"profileGameId\": 78294784,\n"
            + "  \"name\": \"Talv\"\n"
            + "}";
        WebServiceTestUtil.testRetrying
        (
            api.findByRegionAndGameId(Region.EU, "78294784"),
            validCharacter,
            server,
            WebServiceUtil.RETRY_COUNT
        );

        server.shutdown();
        api.setWebClient(oldWebClient);
    }

}
