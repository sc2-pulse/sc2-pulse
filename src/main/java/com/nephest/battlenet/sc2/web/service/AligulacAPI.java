// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.aligulac.AligulacProPlayerRoot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Service
public class AligulacAPI
extends BaseAPI
{

    public static final String BASE_URL = "http://aligulac.com/api/v1/";

    private final String apiKey;

    @Autowired
    public AligulacAPI
    (ObjectMapper objectMapper, @Value("${com.nephest.battlenet.sc2.aligulac.api.key}") String apiKey)
    {
        initClient(objectMapper);
        this.apiKey = apiKey;
    }

    private void initClient(ObjectMapper objectMapper)
    {
        setWebClient(WebServiceUtil.getWebClientBuilder(objectMapper)
            .baseUrl(BASE_URL)
            .build());
    }

    public Mono<AligulacProPlayerRoot> getPlayers(long... ids)
    {
        StringBuilder sb = new StringBuilder("/player/set/");
        for(int i = 0; i < ids.length; i++)
        {
            if(i > 0) sb.append(";");
            sb.append(ids[i]);
        }
        sb.append("/");
        return getWebClient()
            .get()
            .uri(b->b.path(sb.toString()).queryParam("apikey", apiKey).build())
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(AligulacProPlayerRoot.class)
            .retryWhen(getRetry(WebServiceUtil.RETRY));
    }

}
