// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.replaystats.ReplayStatsPlayerCharacter;
import com.nephest.battlenet.sc2.util.TestUtil;
import java.io.IOException;
import java.net.URISyntaxException;
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
import org.springframework.test.context.junit.jupiter.DisabledIf;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
@DisabledIf
(
    expression = "#{environment['com.nephest.battlenet.sc2.replaystats.api.key'] == null}",
    reason = "Authorization token not found",
    loadContext = true
)
public class SC2ReplayStatsAPIIT
{

    public static final String validCharacter;

    static
    {
        try
        {
            validCharacter = TestUtil.readResource
            (
                SC2ReplayStatsAPIIT.class,
                "sc2replaystats/sc2-replay-stats-profile.json"
            );
        }
        catch (IOException | URISyntaxException e)
        {
            throw new IllegalStateException(e);
        }
    }


    @Autowired
    private SC2ReplayStatsAPI api;

    private static WebClient originalClient;

    @BeforeAll
    public static void beforeAll(@Autowired SC2ReplayStatsAPI api, @Autowired DataSource dataSource)
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
    public static void afterAll(@Autowired SC2ReplayStatsAPI api, @Autowired DataSource dataSource)
    throws SQLException
    {
        WebServiceTestUtil.revertFastTimers(api, originalClient);
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }

    @Test
    public void testFindCharacterByNaturalId()
    {
        verifyCharacter(api.findCharacter(PlayerCharacterNaturalId.of(Region.EU, 1, 315071L)).block());
    }

    @Test
    public void testRetrying()
    throws IOException
    {
        WebClient oldWebClient = api.getWebClient();
        try(MockWebServer server = new MockWebServer())
        {
            server.start();
            WebClient timeoutClient = WebServiceTestUtil.createTimeoutClient().mutate()
                .baseUrl(server.url("/someurl").uri().toString())
                .build();
            api.setWebClient(timeoutClient);

            WebServiceTestUtil.testRetrying
            (
                api.findCharacter(PlayerCharacterNaturalId.of(Region.EU, 1, 315071L)),
                validCharacter,
                server,
                0
            );
            server.shutdown();
        }
        finally
        {
            api.setWebClient(oldWebClient);
        }
    }

    public void verifyCharacter(ReplayStatsPlayerCharacter character)
    {
        assertEquals(315071L, character.getId());
        assertEquals(1, character.getRealm());
        assertEquals(Region.EU, character.getRegion());
        assertEquals(125470, character.getReplayStatsId());
        assertNotNull(character.getName());
    }

}
