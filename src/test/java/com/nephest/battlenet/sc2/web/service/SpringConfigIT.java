// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.CoreTestConfig;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class SpringConfigIT
{

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    @Test
    public void testRestTemplateConfig()
    throws IOException
    {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(new MockResponse()
            .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .setBody("LOL")
            .setBodyDelay(CoreTestConfig.IO_TIMEOUT.toMillis() + 500, TimeUnit.MILLISECONDS));
        RestTemplate template = restTemplateBuilder.build();
        try
        {
            template.getForEntity(server.url("/someurl").uri().toString(), Object.class);
        }
        catch (Exception ex)
        {
            if(ExceptionUtils.getRootCause(ex) instanceof SocketTimeoutException) {
                server.shutdown();
                return;
            }
        }
        server.shutdown();
        throw new IllegalStateException("Expected timeout not encountered");
    }

}
