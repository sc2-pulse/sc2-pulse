// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.clickhouse.client.api.Client;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.security.WithBlizzardMockUser;
import com.nephest.battlenet.sc2.discord.DiscordTest;
import com.nephest.battlenet.sc2.discord.IdentifiableEntity;
import com.nephest.battlenet.sc2.discord.InstallationData;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.util.DbTestUtil;
import java.io.IOException;
import javax.sql.DataSource;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

@DiscordTest
@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
public class DiscordAPIIT
{

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

    @Autowired
    private DiscordAPI api;

    private static WebClient originalClient;

    @BeforeEach
    public void beforeEach(@Autowired DataSource dataSource, @Autowired Client clickHouseClient)
    throws Exception
    {
        originalClient = WebServiceTestUtil.fastTimers(api);
        DbTestUtil.initDb(dataSource, clickHouseClient);
    }

    @AfterEach
    public void afterEach(@Autowired DataSource dataSource, @Autowired Client clickHouseClient)
    throws Exception
    {
        WebServiceTestUtil.revertFastTimers(api, originalClient);
        DbTestUtil.clearDb(dataSource, clickHouseClient);
    }

    @Test
    public void testGetBotGuilds()
    {
        assertFalse(api.getBotGuilds().isEmpty());
    }

    @Test
    public void testGetInstallationData()
    {
        InstallationData data = api.getInstallationData().block();

        assertNotNull(data);
        assertTrue(data.guildCount() >= 0);
        assertTrue(data.userCount() >= 0);
    }

    @Disabled("This test can send requests to the Discord API when reauthorizing the client")
    @Test
    @WithBlizzardMockUser(partition = Partition.GLOBAL, username = "tag#1")
    public void whenOauth2Exception_thenRemoveOauth2Client() throws IOException
    {
        WebClient oldWebClient = api.getWebClient();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        OAuth2AuthorizedClient client = WebServiceTestUtil.createOAuth2AuthorizedClient
        (
            clientRegistrationRepository
                .findByRegistrationId(DiscordAPI.USER_CLIENT_REGISTRATION_ID),
            "1"
        );
        oAuth2AuthorizedClientService.saveAuthorizedClient(client, authentication);
        assertNotNull(oAuth2AuthorizedClientService
            .loadAuthorizedClient(DiscordAPI.USER_CLIENT_REGISTRATION_ID, "1"));

        try(MockWebServer server = new MockWebServer())
        {
            server.start();
            api.setWebClient
            (
                oldWebClient.mutate()
                    .baseUrl(server.url("/someurl").uri().toString())
                    .build()
            );
            server.enqueue(new MockResponse().setResponseCode(401));
            api.getGuilds(client, IdentifiableEntity.class)
                .onErrorComplete()
                .blockLast();
            assertNull(oAuth2AuthorizedClientService
                .loadAuthorizedClient(DiscordAPI.USER_CLIENT_REGISTRATION_ID, "1"));
            server.shutdown();
        }
        finally
        {
            api.setWebClient(oldWebClient);
        }
    }

}
