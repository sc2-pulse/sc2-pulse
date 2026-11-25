// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_HTML;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.sc2arcade.SC2ArcadeLadderCharacter;
import com.nephest.battlenet.sc2.model.sc2arcade.SC2ArcadeLadderCoverage;
import com.nephest.battlenet.sc2.model.sc2arcade.SC2ArcadeLadderDivision;
import com.nephest.battlenet.sc2.model.sc2arcade.SC2ArcadeLadderGamePage;
import com.nephest.battlenet.sc2.model.sc2arcade.SC2ArcadeLadderPage;
import com.nephest.battlenet.sc2.model.sc2arcade.SC2ArcadeLadderQueue;
import com.nephest.battlenet.sc2.model.sc2arcade.SC2ArcadeLadderQueuePair;
import com.nephest.battlenet.sc2.model.sc2arcade.SC2ArcadeLadderRanking;
import com.nephest.battlenet.sc2.model.sc2arcade.SC2ArcadeLadderRoster;
import com.nephest.battlenet.sc2.model.sc2arcade.SC2ArcadeLadderSeason;
import com.nephest.battlenet.sc2.model.sc2arcade.SC2ArcadeLadderTierThreshold;
import com.nephest.battlenet.sc2.web.util.RateLimitRefreshConfig;
import com.nephest.battlenet.sc2.web.util.ReactorRateLimiter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

/**
 * Season parameters are region-local Blizzard season ids.
 */
@Service
public class SC2ArcadeLadderAPI
extends BaseAPI
{

    public static final int PAGE_SIZE = 200;
    public static final int CHARACTER_BATCH_SIZE = 100;
    public static final RetryBackoffSpec RETRY = Retry
        .backoff(2, Duration.ofSeconds(1))
        .maxBackoff(Duration.ofSeconds(10))
        .filter(SC2ArcadeLadderAPI::isRetryable);

    private static final String REGION_PATH = "/v2/regions/{region}";
    private static final String QUEUE_PARAMETERS =
        "?queue={queue}&teamType={teamType}&season={season}";
    private static final String LIMIT_PARAMETER = "&limit=" + PAGE_SIZE;
    private static final String CURSOR_PARAMETER = "&cursor={cursor}";

    //all regions use the same host
    private final ReactorRateLimiter rateLimiter;

    @Autowired
    public SC2ArcadeLadderAPI
    (
        ObjectMapper objectMapper,
        @Value("${com.nephest.battlenet.sc2.useragent}") String userAgent,
        @Value("${com.nephest.battlenet.sc2.api.sc2arcade-ladder.url:"
            + "https://s2-svc-ladder.wan.talv.space}") String baseUrl,
        @Value("${com.nephest.battlenet.sc2.api.sc2arcade-ladder"
            + ".requests-per-second:3}") int requestsPerSecond
    )
    {
        this.rateLimiter = new ReactorRateLimiter(new RateLimitRefreshConfig(
            Duration.ofSeconds(1), requestsPerSecond));
        setWebClient(WebServiceUtil.getWebClientBuilder
        (
            WebServiceUtil.CONNECTION_PROVIDER_MEDIUM,
            objectMapper,
            2 * 1024 * 1024
        )
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
            .build());
    }

    private static boolean isRetryable(Throwable t)
    {
        if(t instanceof WebClientResponseException responseException)
        {
            HttpStatusCode status = responseException.getStatusCode();
            return status.is5xxServerError()
                || status.value() == HttpStatus.TOO_MANY_REQUESTS.value();
        }
        return t instanceof WebClientRequestException
            || ExceptionUtils.indexOfType(t, IOException.class) != -1;
    }

    public static boolean isGone(Throwable t)
    {
        return ExceptionUtils
            .indexOfType(t, WebClientResponseException.Gone.class) != -1;
    }

    public static boolean isAccessDenied(Throwable t)
    {
        return ExceptionUtils
            .indexOfType(t, NoRetryException.class) != -1;
    }

    /*
        The service can be hidden behind a login page. Never treat it as data,
        and don't retry.
     */
    private static boolean isAccessDenied(ClientResponse response)
    {
        HttpStatusCode status = response.statusCode();
        if(status.is5xxServerError()) return false;

        MediaType contentType = response.headers().contentType().orElse(null);
        return status.value() == HttpStatus.UNAUTHORIZED.value()
            || status.value() == HttpStatus.FORBIDDEN.value()
            || status.is3xxRedirection()
            || contentType != null && contentType.isCompatibleWith(TEXT_HTML)
            || status.is2xxSuccessful()
                && !APPLICATION_JSON.isCompatibleWith(contentType);
    }

    private static <T> Mono<T> handleResponse
    (
        ClientResponse response,
        ParameterizedTypeReference<T> type
    )
    {
        if(isAccessDenied(response))
        {
            String message = "SC2Arcade ladder API access denied: "
                + response.statusCode()
                + " "
                + response.headers().contentType().orElse(null);
            return response.releaseBody()
                .then(Mono.error(new NoRetryException(message)));
        }
        return response.statusCode().is2xxSuccessful()
            ? response.bodyToMono(type)
            : response.createException().flatMap(Mono::error);
    }

    private <T> Mono<T> get
    (
        ParameterizedTypeReference<T> type,
        String uri,
        Object... uriVariables
    )
    {
        return getWebClient()
            .get()
            .uri(uri, uriVariables)
            .accept(APPLICATION_JSON)
            .exchangeToMono(response->handleResponse(response, type))
            .retryWhen(rateLimiter.retryWhen(RETRY))
            .delaySubscription(Mono.defer(rateLimiter::requestSlot));
    }

    public Mono<SC2ArcadeLadderCoverage> getCoverage(Region region)
    {
        return get
        (
            new ParameterizedTypeReference<>(){},
            REGION_PATH + "/coverage",
            region.name()
        );
    }

    public Mono<SC2ArcadeLadderSeason> getCurrentSeason(Region region)
    {
        return get
        (
            new ParameterizedTypeReference<>(){},
            REGION_PATH + "/seasons/current",
            region.name()
        );
    }

    public Mono<List<SC2ArcadeLadderQueue>> getQueues(Region region, int season)
    {
        return get
        (
            new ParameterizedTypeReference
                <SC2ArcadeLadderPage<SC2ArcadeLadderQueue>>(){},
            REGION_PATH + "/queues?season={season}",
            region.name(),
            season
        )
            .map(SC2ArcadeLadderPage::items);
    }

    public Mono<List<SC2ArcadeLadderTierThreshold>> getTierThresholds
    (
        Region region,
        int season,
        SC2ArcadeLadderQueuePair pair
    )
    {
        return get
        (
            new ParameterizedTypeReference
                <SC2ArcadeLadderPage<SC2ArcadeLadderTierThreshold>>(){},
            REGION_PATH + "/tier-thresholds" + QUEUE_PARAMETERS,
            region.name(),
            pair.queueAlias(),
            pair.teamTypeAlias(),
            season
        )
            .map(SC2ArcadeLadderPage::items);
    }

    public Mono<List<SC2ArcadeLadderDivision>> getDivisions
    (
        Region region,
        int season,
        SC2ArcadeLadderQueuePair pair
    )
    {
        return getPages
        (
            new ParameterizedTypeReference
                <SC2ArcadeLadderPage<SC2ArcadeLadderDivision>>(){},
            "/divisions",
            region,
            season,
            pair
        )
            .flatMapIterable(SC2ArcadeLadderPage::items)
            .collectList();
    }

    /**
     * The cursor expires. Pages are emitted one by one, so the pages that were
     * read before the 410 error are not lost.
     *
     * @param region region
     * @param season region-local Blizzard season id
     * @param pair queue
     * @return ranking pages
     */
    public Flux<SC2ArcadeLadderPage<SC2ArcadeLadderRanking>> getRankings
    (
        Region region,
        int season,
        SC2ArcadeLadderQueuePair pair
    )
    {
        return getPages
        (
            new ParameterizedTypeReference<>(){},
            "/rankings",
            region,
            season,
            pair
        );
    }

    private <T> Flux<SC2ArcadeLadderPage<T>> getPages
    (
        ParameterizedTypeReference<SC2ArcadeLadderPage<T>> type,
        String path,
        Region region,
        int season,
        SC2ArcadeLadderQueuePair pair
    )
    {
        String uri = REGION_PATH + path + QUEUE_PARAMETERS + LIMIT_PARAMETER;
        return get
        (
            type,
            uri,
            region.name(),
            pair.queueAlias(),
            pair.teamTypeAlias(),
            season
        )
            .expand(page->!page.hasNext()
                ? Mono.empty()
                : get
                (
                    type,
                    uri + CURSOR_PARAMETER,
                    region.name(),
                    pair.queueAlias(),
                    pair.teamTypeAlias(),
                    season,
                    page.navigation().next()
                ));
    }

    /**
     * The cursor pins the snapshot, so all pages belong to the same roster.
     *
     * @param region region
     * @param season region-local Blizzard season id
     * @param ladderId ladder id
     * @return complete roster
     */
    public Mono<SC2ArcadeLadderRoster> getRoster
    (
        Region region,
        int season,
        long ladderId
    )
    {
        ParameterizedTypeReference<SC2ArcadeLadderRoster> type =
            new ParameterizedTypeReference<>(){};
        String uri = REGION_PATH + "/ladders/{ladderId}?season={season}"
            + LIMIT_PARAMETER;
        return get(type, uri, region.name(), ladderId, season)
            .expand(page->!page.hasNext()
                ? Mono.empty()
                : get
                (
                    type,
                    uri + CURSOR_PARAMETER,
                    region.name(),
                    ladderId,
                    season,
                    page.navigation().next()
                ))
            .collectList()
            .map(pages->new SC2ArcadeLadderRoster
            (
                pages.get(0).snapshot(),
                pages.stream().flatMap(p->p.items().stream()).toList(),
                null
            ));
    }

    /**
     * Unknown characters are not returned.
     *
     * @param region region
     * @param characters up to {@link #CHARACTER_BATCH_SIZE} characters of the
     * region
     * @return found characters
     */
    public Mono<List<SC2ArcadeLadderCharacter>> getCharacters
    (
        Region region,
        List<? extends PlayerCharacterNaturalId> characters
    )
    {
        if(characters.isEmpty()) return Mono.just(List.of());
        if(characters.size() > CHARACTER_BATCH_SIZE)
            throw new IllegalArgumentException("Too many characters");

        return get
        (
            new ParameterizedTypeReference
                <SC2ArcadeLadderPage<SC2ArcadeLadderCharacter>>(){},
            REGION_PATH + "/characters?toonHandle={toonHandles}",
            region.name(),
            characters.stream()
                .map(PlayerCharacterNaturalId::toToonHandle)
                .collect(Collectors.joining(","))
        )
            .map(SC2ArcadeLadderPage::items);
    }

    public Mono<List<SC2ArcadeLadderCharacter>> getTeamMembers
    (
        Region region,
        int season,
        String teamId
    )
    {
        return get
        (
            new ParameterizedTypeReference
                <SC2ArcadeLadderPage<SC2ArcadeLadderCharacter>>(){},
            REGION_PATH + "/teams/{teamId}/members?season={season}",
            region.name(),
            teamId,
            season
        )
            .map(SC2ArcadeLadderPage::items);
    }

    /**
     * Games in order of their end time. A game can be published hours after its
     * end.
     *
     * @param region region
     * @param from min end time, inclusive
     * @param to max end time
     * @param cursor {@link SC2ArcadeLadderGamePage#navigation()} of the
     * previous page, null for the first page
     * @return games of all types
     */
    public Mono<SC2ArcadeLadderGamePage> getGames
    (
        Region region,
        Instant from,
        Instant to,
        @Nullable String cursor
    )
    {
        ParameterizedTypeReference<SC2ArcadeLadderGamePage> type =
            new ParameterizedTypeReference<>(){};
        String uri = REGION_PATH
            + "/games?order=ended&direction=asc&from={from}&to={to}"
            + LIMIT_PARAMETER;
        return cursor == null
            ? get(type, uri, region.name(), from, to)
            : get
            (
                type,
                uri + CURSOR_PARAMETER,
                region.name(),
                from,
                to,
                cursor
            );
    }

}
