// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.aligulac.AligulacProPlayer;
import java.util.stream.LongStream;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class AligulacAPIIT
{

    @Autowired
    private AligulacAPI api;

    @Autowired
    private ProPlayerService proPlayerService;

    private static WebClient originalClient;

    @BeforeAll
    public static void beforeAll(@Autowired AligulacAPI api)
    {
        originalClient = WebServiceTestUtil.fastTimers(api);
    }

    @AfterAll
    public static void afterAll(@Autowired AligulacAPI api)
    {
        WebServiceTestUtil.revertFastTimers(api, originalClient);
    }

    @Test
    @Order(1)
    public void testFetch()
    {
        Long[] batch = LongStream.range(1L, proPlayerService.getAligulacBatchSize() + 1)
            .boxed()
            .toArray(Long[]::new);
        AligulacProPlayer[] players = api.getPlayers(batch).block().getObjects();
        assertEquals(players.length, batch.length);
        for(AligulacProPlayer player : players)
        {
            assertNotNull(player.getId());
            assertNotNull(player.getTotalEarnings());
            assertNotNull(player.getCountry());
        }
    }

    @Test @Order(2)
    public void testRetrying()
    throws Exception
    {
        MockWebServer server = new MockWebServer();
        server.start();
        api.setWebClient(WebServiceTestUtil.createTimeoutClient().mutate().baseUrl(server.url("/").uri().toString()).build());
        WebServiceTestUtil.testRetrying(api.getPlayers(), "{\"objects\": []}", server, WebServiceUtil.RETRY_COUNT - 1);
        server.shutdown();
    }

}
