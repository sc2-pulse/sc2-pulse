// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;

public class WebServiceTestUtil
{

    public static final Duration OPERATION_DURATION = Duration.ofMillis(100);

    public static WebClient createTimeoutClient()
    {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) OPERATION_DURATION.toMillis())
            .doOnConnected
            (
                c-> c.addHandlerLast(new ReadTimeoutHandler(OPERATION_DURATION.toMillis(), TimeUnit.MILLISECONDS))
                    .addHandlerLast(new WriteTimeoutHandler(OPERATION_DURATION.toMillis(), TimeUnit.MILLISECONDS))
            )
            .compress(true);
        WebClient client = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
        return client;
    }

    public static void testRetrying(Mono<?> mono, String body, MockWebServer server, int count)
    {
        testRetryingOnErrorCodes(mono, body, server, count);
        testRetryingOnMalformedBody(mono, body, server, count);
        testRetryingOnTimeout(mono, body, server, count);
    }

    private static void testRetryingOnErrorCodes(Mono<?> mono, String body, MockWebServer server, int count)
    {
        for(int i = 0; i < count; i++) server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(body));

        StepVerifier.create(mono)
            .expectNextCount(1)
            .expectComplete().verify();
    }

    private static void testRetryingOnMalformedBody(Mono<?> mono, String body, MockWebServer server, int count)
    {
        for(int i = 0; i < count; i++)
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("dadsdcz"));
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(body));

        StepVerifier.create(mono)
            .expectNextCount(1)
            .expectComplete().verify();
    }

    private static void testRetryingOnTimeout(Mono<?> mono, String body, MockWebServer server, int count)
    {
        System.out.println("Testing socket timeouts, might take some time...");
        MockResponse dr = new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBodyDelay(OPERATION_DURATION.toMillis() + 1000 , TimeUnit.MILLISECONDS)
            .setBody(body);
        for(int i = 0; i < count; i++) server.enqueue(dr);
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(body));

        StepVerifier.create(mono)
            .expectNextCount(1)
            .expectComplete().verify();
    }

    public static WebClient fastTimers(BaseAPI api)
    {
        WebClient originalClient = api.getWebClient();
        api.setWebClient(originalClient.mutate()
            .clientConnector(new ReactorClientHttpConnector(WebServiceUtil.getHttpClient(Duration.ZERO, Duration.ZERO)))
            .build());
        api.setRetry(Retry.maxInARow(WebServiceUtil.RETRY_COUNT));
        return originalClient;
    }

    public static void revertFastTimers(BaseAPI api, WebClient originalClient)
    {
        api.setWebClient(originalClient);
        api.setRetry(null);
    }

    public static <T> T getObject
    (
        MockMvc mvc,
        ObjectMapper objectMapper,
        Class<T> clazz,
        String uriTemplate,
        Object... uriArgs
    )
    throws Exception
    {
        return objectMapper.readValue(mvc.perform
        (
            get(uriTemplate, uriArgs)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), clazz);
    }

    public static <T> T getObject
    (
        MockMvc mvc,
        ObjectMapper objectMapper,
        TypeReference<T> clazz,
        String uriTemplate,
        Object... uriArgs
    )
    throws Exception
    {
        return objectMapper.readValue(mvc.perform
        (
            get(uriTemplate, uriArgs)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), clazz);
    }

}
