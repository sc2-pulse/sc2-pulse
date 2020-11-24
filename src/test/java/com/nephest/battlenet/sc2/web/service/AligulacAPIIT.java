// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.aligulac.AligulacProPlayer;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class AligulacAPIIT
{

    @Autowired
    private AligulacAPI api;

    @Autowired
    private ProPlayerService proPlayerService;

    @Test
    @Order(1)
    public void testFetch()
    {
        long[] batch = new long[proPlayerService.getAligulacBatchSize()];
        for(int i = 0; i < batch.length; i++) batch[i] = i + 1;
        AligulacProPlayer[] players = api.getPlayers(batch).block().getObjects();
        assertEquals(players.length, batch.length);
        for(AligulacProPlayer player : players)
        {
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
