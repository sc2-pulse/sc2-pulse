// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertNull;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import com.nephest.battlenet.sc2.model.Region;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class EmptyApiResponseIT
{

    private static SC2ReplayStatsAPI sc2ReplayStatsAPI;
    private static SC2ArcadeAPI sc2ArcadeAPI;
    private static final MockWebServer server = new MockWebServer();
    private static final Map<BaseAPI, WebClient> originalClients = new HashMap<>();

    private static void addOriginalClient(BaseAPI baseAPI)
    {
        if(baseAPI == null) return;
        originalClients.put(baseAPI, baseAPI.getWebClient());
    }

    @BeforeAll
    public static void beforeAll
    (
        @Autowired(required = false) SC2ReplayStatsAPI sc2ReplayStatsAPI,
        @Autowired SC2ArcadeAPI sc2ArcadeAPI
    )
    throws IOException
    {
        EmptyApiResponseIT.sc2ReplayStatsAPI = sc2ReplayStatsAPI;
        EmptyApiResponseIT.sc2ArcadeAPI = sc2ArcadeAPI;
        addOriginalClient(sc2ReplayStatsAPI);
        addOriginalClient(sc2ArcadeAPI);
        server.start();
        whenEmptyStatusResponse_thenReturnEmptyPublisher().forEach(args->
            WebServiceUtil.EMPTY_STATUS_CODES.forEach(code->
                server.enqueue
                (
                    new MockResponse()
                        .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .setResponseCode(code.value())
                        .setBody("Irrelevant body")
                )
            )
        );
        originalClients.forEach
        (
            (api, originalClient)->api.setWebClient
            (
                originalClient.mutate()
                    .baseUrl(server.url("/someUrl").uri().toString())
                    .build()
            )
        );
    }

    @AfterAll
    public static void afterAll()
    throws Exception
    {
        try
        {
            server.close();
            server.shutdown();
        }
        finally
        {
            originalClients.forEach(BaseAPI::setWebClient);
        }
    }

    public static Stream<Arguments> whenEmptyStatusResponse_thenReturnEmptyPublisher()
    {
        return Stream.of
        (
            Arguments.of
            (
                (Supplier<Mono<?>>)()->sc2ReplayStatsAPI.findCharacter
                (
                    PlayerCharacterNaturalId.of(Region.EU, 1, 315071L)
                )
            ),
            Arguments.of
            (
                (Supplier<Mono<?>>)()->sc2ArcadeAPI.findCharacter
                (
                    PlayerCharacterNaturalId.of(Region.EU, 1, 315071L)
                )
            ),
            Arguments.of
            (
                (Supplier<Mono<?>>)()->sc2ArcadeAPI.findByRegionAndGameId(Region.EU, "123")
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    public void whenEmptyStatusResponse_thenReturnEmptyPublisher(Supplier<Mono<?>> responseSupplier)
    {
        for(HttpStatusCode ignored : WebServiceUtil.EMPTY_STATUS_CODES)
            assertNull(responseSupplier.get().block());
    }

}
