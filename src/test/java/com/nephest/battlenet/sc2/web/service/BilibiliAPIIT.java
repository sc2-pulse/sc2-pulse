// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static com.nephest.battlenet.sc2.web.service.BilibiliAPI.STAR_CRAFT_2_AREA_ID;
import static com.nephest.battlenet.sc2.web.service.BilibiliAPI.STAR_CRAFT_2_PARENT_AREA_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.bilibili.BilibiliStream;
import java.util.List;
import okhttp3.mockwebserver.MockWebServer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

@Disabled("Bilibili is disabled until they have a proper API")
@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class BilibiliAPIIT
{

    @Autowired
    private BilibiliAPI api;

    private static WebClient originalClient;

    @BeforeAll
    public static void beforeAll(@Autowired BilibiliAPI api)
    {
        originalClient = WebServiceTestUtil.fastTimers(api);
    }

    @AfterAll
    public static void afterAll(@Autowired BilibiliAPI api)
    {
        WebServiceTestUtil.revertFastTimers(api, originalClient);
    }

    @Test
    public void testFetch()
    {
        List<BilibiliStream> streams = api
            .getStreams(STAR_CRAFT_2_PARENT_AREA_ID, STAR_CRAFT_2_AREA_ID)
            .collectList()
            .block();
        assertFalse(streams.isEmpty());
        for(BilibiliStream stream : streams)
        {
            Assertions.assertThat(stream).hasNoNullFieldsOrProperties();
            assertEquals(STAR_CRAFT_2_PARENT_AREA_ID, stream.getParentId());
            assertEquals(STAR_CRAFT_2_AREA_ID, stream.getAreaId());
        }
    }

    @Test
    public void testRetrying()
    throws Exception
    {
        WebClient originalClient = api.getWebClient();
        try(MockWebServer server = new MockWebServer())
        {
            server.start();
            api.setWebClient(WebServiceTestUtil.createTimeoutClient().mutate()
                .baseUrl(server.url("/").uri().toString()).build());
            WebServiceTestUtil.testRetrying
            (
                api.getStreams(STAR_CRAFT_2_PARENT_AREA_ID, STAR_CRAFT_2_AREA_ID).collectList(),
                "{\"data\": {\"list\": [], \"hasMore\": 0}}",
                server,
                WebServiceUtil.RETRY_COUNT - 1
            );
        }
        finally
        {
            api.setWebClient(originalClient);
        }
    }

}
