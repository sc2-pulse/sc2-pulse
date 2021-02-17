// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

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

}
