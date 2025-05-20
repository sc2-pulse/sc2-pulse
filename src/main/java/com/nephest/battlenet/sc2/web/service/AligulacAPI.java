// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.aligulac.AligulacProPlayerRoot;
import com.nephest.battlenet.sc2.web.util.ReactorRateLimiter;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AligulacAPI
extends BaseAPI
{

    public static final String BASE_URL = "http://aligulac.com/api/v1/";
    public static final int SLOTS_PER_PERIOD = 2;

    private final String apiKey;
    private final ReactorRateLimiter rateLimiter = new ReactorRateLimiter();

    @Autowired
    public AligulacAPI
    (
        ObjectMapper objectMapper,
        @Value("${com.nephest.battlenet.sc2.aligulac.api.key}") String apiKey,
        @Value("${com.nephest.battlenet.sc2.useragent}") String userAgent
    )
    {
        initClient(objectMapper, userAgent);
        this.apiKey = apiKey;
    }

    @Scheduled(cron="* * * * * *")
    public void refreshReactorSlots()
    {
        rateLimiter.refreshSlots(SLOTS_PER_PERIOD);
    }

    private void initClient(ObjectMapper objectMapper, String userAgent)
    {
        setWebClient(WebServiceUtil.getWebClientBuilder(objectMapper)
            .baseUrl(BASE_URL)
            .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
            .build());
    }

    public Mono<AligulacProPlayerRoot> getPlayers(Set<Long> ids)
    {
        if(ids.isEmpty()) return Mono.empty();

        String url = "/player/set/"
            + ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(";"))
            + "/";
        return getWebClient()
            .get()
            .uri(b->b.path(url).queryParam("apikey", apiKey).build())
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(AligulacProPlayerRoot.class)
            .retryWhen(rateLimiter.retryWhen(getRetry(WebServiceUtil.RETRY)))
            .delaySubscription(rateLimiter.requestSlot());
    }

}
