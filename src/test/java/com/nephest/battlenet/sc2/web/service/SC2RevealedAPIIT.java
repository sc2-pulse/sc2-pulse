// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.revealed.RevealedProPlayer;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class SC2RevealedAPIIT
{

    @Autowired
    private SC2RevealedAPI api;

    private static WebClient originalClient;

    @BeforeAll
    public static void beforeAll(@Autowired SC2RevealedAPI api)
    {
        originalClient = WebServiceTestUtil.fastTimers(api);
    }

    @AfterAll
    public static void afterAll(@Autowired SC2RevealedAPI api)
    {
        WebServiceTestUtil.revertFastTimers(api, originalClient);
    }

    @Test
    @Order(1)
    public void testFetch()
    {
        RevealedProPlayer[] players = api.getPlayers().block().getPlayers();
        assertTrue(players.length > 0);
        for(RevealedProPlayer player : players)
        {
            if(player.getFirstName() != null && !player.getFirstName().isEmpty())
                assertFalse(player.getPlayer().isEmpty());
            assertTrue(player.get_id() != null && player.get_id().length() > 0);
            for(String url : player.getSocialMedia().values()) assertTrue(url != null && !url.isEmpty());
        }
    }

    @Test @Order(2)
    public void testRetrying()
    throws Exception
    {
        MockWebServer server = new MockWebServer();
        server.start();
        api.setWebClient(WebServiceTestUtil.createTimeoutClient().mutate().baseUrl(server.url("/").uri().toString()).build());
        WebServiceTestUtil.testRetrying(api.getPlayers(), "{\"players\": []}", server, WebServiceUtil.RETRY_COUNT - 1);
        server.shutdown();
    }

}
