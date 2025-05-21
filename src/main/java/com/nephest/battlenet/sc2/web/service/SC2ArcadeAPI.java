// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.arcade.ArcadePlayerCharacter;
import com.nephest.battlenet.sc2.web.util.RateLimitData;
import com.nephest.battlenet.sc2.web.util.ReactorRateLimiter;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.HttpHeaders;
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
        @Qualifier("sc2StatsConversionService") ConversionService conversionService,
        @Value("${com.nephest.battlenet.sc2.useragent}") String userAgent
    )
    {
        this.conversionService = conversionService;
        initClient(objectMapper, userAgent);
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

    private void initClient(ObjectMapper objectMapper, String userAgent)
    {
        setWebClient(WebServiceUtil.getWebClientBuilder(
                WebServiceUtil.CONNECTION_PROVIDER_MEDIUM, objectMapper)
            .baseUrl(BASE_URL)
            .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
            .build());
    }

    private RateLimitData getRateLimitData(ClientResponse response)
    {
        return WebServiceUtil.parseRateLimit(response.headers().asHttpHeaders());
    }

    private <T> Mono<T> readRequestRateAndExchangeToMono(ClientResponse response, Class<T> clazz)
    {

        if(!response.headers().header(WebServiceUtil.RATE_LIMIT_LIMIT_HEADER_NAME).isEmpty())
            rateLimiter.update(getRateLimitData(response)).subscribe();
        return WebServiceUtil.bodyToMonoErrorOnErrorCode(response, clazz);
    }

    /**
     * Find a character by region and game id. Useful when searching by in-game links.
     * @param region region
     * @param gameId in-game id, unsigned long, reversed byte order of in-game id
     * @return character
     */
    @Cacheable(cacheNames = "arcade-character-search")
    public Mono<ArcadePlayerCharacter> findByRegionAndGameId(Region region, long gameId)
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
            .exchangeToMono(resp->readRequestRateAndExchangeToMono(resp, ArcadePlayerCharacter.class))
            .retryWhen(rateLimiter.retryWhen(getRetry(WebServiceUtil.RETRY)))
            .delaySubscription(Mono.defer(rateLimiter::requestSlot));
    }

    public Mono<ArcadePlayerCharacter> findByRegionAndGameId(Region region, String gameId)
    {
        return nestedApi.findByRegionAndGameId(region, Long.parseUnsignedLong(gameId));
    }

    @Cacheable(cacheNames = "arcade-character-search")
    public Mono<ArcadePlayerCharacter> findCharacter(PlayerCharacterNaturalId naturalId)
    {
        return getWebClient()
            .get()
            .uri
            (
                "/profiles/{0}/{1}/{2}",
                conversionService.convert(naturalId.getRegion(), Integer.class),
                naturalId.getRealm(),
                naturalId.getBattlenetId()
            )
            .accept(APPLICATION_JSON)
            .exchangeToMono(resp->readRequestRateAndExchangeToMono(resp, ArcadePlayerCharacter.class))
            .retryWhen(rateLimiter.retryWhen(getRetry(WebServiceUtil.RETRY)))
            .delaySubscription(Mono.defer(rateLimiter::requestSlot));
    }

}
