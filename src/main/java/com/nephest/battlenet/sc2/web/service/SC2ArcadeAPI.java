// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardFullPlayerCharacter;
import com.nephest.battlenet.sc2.web.util.ReactorRateLimiter;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class SC2ArcadeAPI
extends BaseAPI
{

    public static final String BASE_URL = "https://api.sc2arcade.com/";
    public static final int REQUESTS_PER_PERIOD = 100;
    public static final Duration REQUEST_SLOT_REFRESH_DURATION = Duration.ofSeconds(45);

    private final ReactorRateLimiter rateLimiter = new ReactorRateLimiter();
    private final ConversionService conversionService;

    @Autowired
    public SC2ArcadeAPI
    (
        ObjectMapper objectMapper,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.conversionService = conversionService;
        initClient(objectMapper);
        Flux.interval(Duration.ofSeconds(0), REQUEST_SLOT_REFRESH_DURATION).doOnNext(i->refreshReactorSlots()).subscribe();
    }

    private void initClient(ObjectMapper objectMapper)
    {
        setWebClient(WebServiceUtil.getWebClientBuilder(objectMapper)
            .baseUrl(BASE_URL)
            .build());
    }

    private void refreshReactorSlots()
    {
        rateLimiter.refreshSlots(REQUESTS_PER_PERIOD);
    }

    /**
     * Find a character by region and game id. Useful when searching by in-game links.
     * @param region region
     * @param gameId in-game id, unsigned long, reversed byte order of in-game id
     * @return character
     */
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
            .retrieve()
            .bodyToMono(BlizzardFullPlayerCharacter.class)
            .retryWhen(rateLimiter.retryWhen(getRetry(WebServiceUtil.RETRY)))
            .delaySubscription(rateLimiter.requestSlot());
    }

    public Mono<BlizzardFullPlayerCharacter> findByRegionAndGameId(Region region, String gameId)
    {
        return findByRegionAndGameId(region, Long.parseUnsignedLong(gameId));
    }

}
