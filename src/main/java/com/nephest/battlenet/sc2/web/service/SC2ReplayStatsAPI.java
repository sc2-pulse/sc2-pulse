// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import com.nephest.battlenet.sc2.model.replaystats.ReplayStats;
import com.nephest.battlenet.sc2.model.replaystats.ReplayStatsPlayerCharacter;
import com.nephest.battlenet.sc2.web.util.ReactorRateLimiter;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@ReplayStats
public class SC2ReplayStatsAPI
extends BaseAPI
{

    public static final String BASE_URL = "https://api.sc2replaystats.com";
    public static final Duration REQUEST_SLOT_REFRESH_DURATION = Duration.ofMillis(200);

    private final String authorizationToken;
    private final ReactorRateLimiter rateLimiter = new ReactorRateLimiter();

    @Autowired
    public SC2ReplayStatsAPI
    (
        ObjectMapper objectMapper,
        @Value("${com.nephest.battlenet.sc2.replaystats.api.key}") String authorizationToken,
        @Value("${com.nephest.battlenet.sc2.useragent}") String userAgent
    )
    {
        initClient(objectMapper, userAgent);
        this.authorizationToken = authorizationToken;
        rateLimiter.refreshSlots(1);
    }

    private void initClient(ObjectMapper objectMapper, String userAgent)
    {
        setWebClient(WebServiceUtil.getWebClientBuilder(
                WebServiceUtil.CONNECTION_PROVIDER_MEDIUM, objectMapper)
            .baseUrl(BASE_URL)
            .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
            .build());
    }

    public static String generateCharacterId(PlayerCharacterNaturalId naturalId)
    {
        return naturalId.getBattlenetId()
            + "." + naturalId.getRealm()
            + "." + naturalId.getRegion().name();
    }

    private void onRequestComplete(Object obj)
    {
        Mono.fromRunnable(()->rateLimiter.refreshSlots(1))
            .delaySubscription(REQUEST_SLOT_REFRESH_DURATION)
            .subscribe();
    }

    @Cacheable(cacheNames = "replay-stats-character-search")
    public Mono<ReplayStatsPlayerCharacter> findCharacter(PlayerCharacterNaturalId naturalId)
    {
        return getWebClient()
            .get()
            .uri
            (
                "/player/{1}",
                generateCharacterId(naturalId)
            )
            .header("Authorization", authorizationToken)
            .accept(APPLICATION_JSON)
            .exchangeToMono
            (
                resp->WebServiceUtil.handleMonoResponse(resp, ReplayStatsPlayerCharacter.class)
            )
            .doOnSuccess(this::onRequestComplete)
            .doOnError(this::onRequestComplete)
            .delaySubscription(Mono.defer(rateLimiter::requestSlot));
    }

}
