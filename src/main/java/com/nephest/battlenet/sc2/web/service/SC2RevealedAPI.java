// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.revealed.RevealedPlayers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Service
public class SC2RevealedAPI
{

    public static final String BASE_URL = "https://spawning-pool.herokuapp.com/";

    private WebClient client;

    @Autowired
    public SC2RevealedAPI(ObjectMapper objectMapper)
    {
        initClient(objectMapper);
    }

    private void initClient(ObjectMapper objectMapper)
    {
        client = WebServiceUtil.getWebClientBuilder(objectMapper, 800000)
            .baseUrl(BASE_URL)
            .build();
    }

    protected void setWebClient(WebClient client)
    {
        this.client = client;
    }

    protected WebClient getWebClient()
    {
        return client;
    }

    public Mono<RevealedPlayers> getPlayers()
    {
        return getWebClient()
            .get()
            .uri("/players")
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(RevealedPlayers.class)
            .retryWhen(WebServiceUtil.RETRY);
    }

}
