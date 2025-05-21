// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.revealed.RevealedPlayers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class SC2RevealedAPI
extends BaseAPI
{

    public static final String BASE_URL = "https://spawning-pool.herokuapp.com/";

    @Autowired
    public SC2RevealedAPI
    (
        ObjectMapper objectMapper,
        @Value("${com.nephest.battlenet.sc2.useragent}") String userAgent
    )
    {
        initClient(objectMapper, userAgent);
    }

    private void initClient(ObjectMapper objectMapper, String userAgent)
    {
        setWebClient(WebServiceUtil.getWebClientBuilder(
                WebServiceUtil.CONNECTION_PROVIDER_MEDIUM, objectMapper, 800000)
            .baseUrl(BASE_URL)
            .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
            .build());
    }

    @Deprecated
    public Mono<RevealedPlayers> getPlayers()
    {
        return getWebClient()
            .get()
            .uri("/players")
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(RevealedPlayers.class)
            .retryWhen(getRetry(WebServiceUtil.RETRY));
    }

}
