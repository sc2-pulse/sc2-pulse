// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.blizzard;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLeague;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardSeason;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTierDivision;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.TcpClient;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class BlizzardSC2APIIT
{

    public static final int RETRY_COUNT = 2;
    public static final Duration OPERATION_DURATION = Duration.ofMillis(100);
    public static final String VALID_SEASON = "{\"seasonId\": 1, \"year\": 2010, \"number\": 1}";
    public static final String VALID_LEAGUE = "{\"type\": 0, \"queueType\": 201, \"teamType\": 0, \"tier\": []}";
    public static final String VALID_LADDER = "{\"team\": []}";

    @Autowired
    private BlizzardSC2API api;

    @Test @Order(1)
    public void testFetch()
    {
        BlizzardSeason season = api.getCurrentSeason(Region.EU).block();
        assertNotNull(season);
        assertNotNull(season.getId());
        assertNotNull(season.getNumber());
        assertNotNull(season.getYear());
        assertNotNull(season.getStart());
        assertNotNull(season.getEnd());
    }

    @Test @Order(2)
    public void testRetrying()
    throws Exception
    {
        MockWebServer server = new MockWebServer();
        server.start();
        TcpClient timeoutClient = TcpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) OPERATION_DURATION.toMillis())
            .doOnConnected
            (
                c-> c.addHandlerLast(new ReadTimeoutHandler(OPERATION_DURATION.toMillis(), TimeUnit.MILLISECONDS))
                    .addHandlerLast(new WriteTimeoutHandler(OPERATION_DURATION.toMillis(), TimeUnit.MILLISECONDS))
            );
        HttpClient httpClient = HttpClient.from(timeoutClient)
            .compress(true);
        WebClient client = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
        api.setRegionUri(server.url("/someurl").uri().toString());
        api.setWebClient(client);

        testRetrying(api.getCurrentSeason(Region.EU), VALID_SEASON, server, RETRY_COUNT);
        testRetrying
        (
            api.getLeague
            (
                Region.EU,
                mock(BlizzardSeason.class),
                BlizzardLeague.LeagueType.BRONZE,
                QueueType.LOTV_1V1,
                TeamType.ARRANGED
            ),
            VALID_LEAGUE, server, RETRY_COUNT
        );
        testRetrying(api.getLadder(Region.EU, mock(BlizzardTierDivision.class)), VALID_LADDER, server, RETRY_COUNT);
        server.shutdown();
    }

    private void testRetrying(Mono<?> mono, String body, MockWebServer server, int count)
    throws Exception
    {
        testRetryingOnErrorCodes(mono, body, server, count);
        testRetryingOnMalformedBody(mono, body, server, count);
        testRetryingOnTimeout(mono, body, server, count);
    }

    private void testRetryingOnErrorCodes(Mono<?> mono, String body, MockWebServer server, int count)
    {
        for(int i = 0; i < count; i++) server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(body));

        StepVerifier.create(mono)
            .expectNextCount(1)
            .expectComplete().verify();
    }

    private void testRetryingOnMalformedBody(Mono<?> mono, String body, MockWebServer server, int count)
    {
        for(int i = 0; i < count; i++)
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("dadsdcz"));
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(body));

        StepVerifier.create(mono)
            .expectNextCount(1)
            .expectComplete().verify();
    }

    private void testRetryingOnTimeout(Mono<?> mono, String body, MockWebServer server, int count)
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
