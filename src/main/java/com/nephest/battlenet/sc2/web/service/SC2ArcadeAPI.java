// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardFullPlayerCharacter;
import com.nephest.battlenet.sc2.web.util.RateLimitData;
import com.nephest.battlenet.sc2.web.util.ReactorRateLimiter;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class SC2ArcadeAPI
extends BaseAPI
{

    public static final String BASE_URL = "https://api.sc2arcade.com/";
    public static final int REQUESTS_PER_PERIOD = 100;
    public static final Duration REQUEST_SLOT_REFRESH_DURATION = Duration.ofSeconds(40);

    private final ReactorRateLimiter rateLimiter = new ReactorRateLimiter();
    private final ConversionService conversionService;

    @Autowired @Lazy
    private SC2ArcadeAPI nestedApi;

    @Autowired
    public SC2ArcadeAPI
    (
        ObjectMapper objectMapper,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.conversionService = conversionService;
        initClient(objectMapper);
        Flux.interval(Duration.ofSeconds(0), REQUEST_SLOT_REFRESH_DURATION)
            .doOnNext(i->rateLimiter.refreshUndeterminedSlots(REQUEST_SLOT_REFRESH_DURATION, REQUESTS_PER_PERIOD))
            .subscribe();
    }

    protected SC2ArcadeAPI getNestedApi()
    {
        return nestedApi;
    }

    protected void setNestedApi(SC2ArcadeAPI nestedApi)
    {
        this.nestedApi = nestedApi;
    }

    private void initClient(ObjectMapper objectMapper)
    {
        setWebClient(WebServiceUtil.getWebClientBuilder(objectMapper)
            .baseUrl(BASE_URL)
            .build());
    }

    private RateLimitData getRateLimitData(ClientResponse response)
    {
        return WebServiceUtil.parseRateLimit(response.headers().asHttpHeaders());
    }

    private <T> Mono<T> readRequestRateAndExchangeToMono(ClientResponse response, Class<T> clazz)
    {
        rateLimiter.update(getRateLimitData(response)).subscribe();
        return response.bodyToMono(clazz);
    }

    /**
     * Find a character by region and game id. Useful when searching by in-game links.
     * @param region region
     * @param gameId in-game id, unsigned long, reversed byte order of in-game id
     * @return character
     */
    @Cacheable(cacheNames = "profile-search-game-id")
    public Mono<BlizzardFullPlayerCharacter> findByRegionAndGameId(Region region, long gameId)
    {
        return getWebClient()
            .get()
            .uri
            (
                "/profiles/{0}/{1}",
                conversionService.convert(region, Integer.class),
                Long.toUnsignedString(gameId)
            )
            .accept(APPLICATION_JSON)
            .exchangeToMono(resp->readRequestRateAndExchangeToMono(resp, BlizzardFullPlayerCharacter.class))
            .retryWhen(rateLimiter.retryWhen(getRetry(WebServiceUtil.RETRY)))
            .delaySubscription(rateLimiter.requestSlot())
            .cache
            (
                (m)->
                    WebServiceUtil.DEFAULT_API_CACHE_DURATION,
                    (t)->Duration.ZERO,
                    ()->WebServiceUtil.DEFAULT_API_CACHE_DURATION
            );
    }

    public Mono<BlizzardFullPlayerCharacter> findByRegionAndGameId(Region region, String gameId)
    {
        return nestedApi.findByRegionAndGameId(region, Long.parseUnsignedLong(gameId));
    }

}
