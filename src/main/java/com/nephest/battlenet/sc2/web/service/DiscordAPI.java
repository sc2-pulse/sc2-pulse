// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.springframework.http.MediaType.ALL;
import static org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId;
import static org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.discord.Discord;
import com.nephest.battlenet.sc2.discord.SpringDiscordClient;
import com.nephest.battlenet.sc2.discord.connection.ApplicationRoleConnection;
import com.nephest.battlenet.sc2.discord.connection.ConnectionMetaData;
import com.nephest.battlenet.sc2.model.discord.DiscordConnection;
import com.nephest.battlenet.sc2.model.discord.DiscordUser;
import com.nephest.battlenet.sc2.web.util.ReactorRateLimiter;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.RetrySpec;

/**
 * Uses existing {@link SpringDiscordClient} and reuses its rate limiter. Also uses its own rate
 * limiter on top of it to prevent any issues.
 */
@Service
@Discord
public class DiscordAPI
{

    private static final Logger LOG = LoggerFactory.getLogger(DiscordAPI.class);

    public static final String USER_CLIENT_REGISTRATION_ID = "discord-lg";
    public static final String BASE_URL = "https://discord.com/api";
    public static final Duration REQUEST_SLOT_REFRESH_TIME = Duration.ofMillis(1100);
    public static final int DEFAULT_REQUESTS_PER_SECOND = 5;
    public static final int MAX_REQUESTS_PER_SECOND = 5;
    public static final String RATE_LIMIT_HEADER_PREFIX = "X-RateLimit-";
    public static final String RATE_LIMIT_LIMIT_HEADER = RATE_LIMIT_HEADER_PREFIX + "Limit";
    public static final RetrySpec RETRY_WHEN_TOO_MANY_REQUESTS = RetrySpec
        .max(1)
        .filter(t->t instanceof WebClientResponseException.TooManyRequests
            || ExceptionUtils.getRootCause(t) instanceof WebClientResponseException.TooManyRequests)
        .transientErrors(true);

    private final SpringDiscordClient discordClient;
    private final OAuth2AuthorizedClientService auth2AuthorizedClientService;
    private WebClient client;
    private final ReactorRateLimiter rateLimiter = new ReactorRateLimiter();
    private final AtomicInteger requestsPerSecond = new AtomicInteger(DEFAULT_REQUESTS_PER_SECOND);
    private final String applicationId;
    private final String token;

    @Autowired
    public DiscordAPI
    (
        SpringDiscordClient discordClient,
        ObjectMapper objectMapper,
        OAuth2AuthorizedClientManager auth2AuthorizedClientManager,
        OAuth2AuthorizedClientService auth2AuthorizedClientService,
        @Value("${spring.security.oauth2.client.registration.discord-lg.client-id}") String applicationId,
        @Value("${discord.token}") String token
    )
    {
        this.discordClient = discordClient;
        this.auth2AuthorizedClientService = auth2AuthorizedClientService;
        initWebClient(objectMapper, auth2AuthorizedClientManager);
        Flux.interval(Duration.ofSeconds(0), REQUEST_SLOT_REFRESH_TIME)
            .doOnNext(i->rateLimiter.refreshSlots(requestsPerSecond.get())).subscribe();
        this.applicationId = applicationId;
        this.token = token;
    }

    private void initWebClient(ObjectMapper objectMapper, OAuth2AuthorizedClientManager auth2AuthorizedClientManager)
    {
        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Client =
            new ServletOAuth2AuthorizedClientExchangeFilterFunction(auth2AuthorizedClientManager);
        client = WebServiceUtil.getWebClientBuilder(objectMapper, 5 * 1024, ALL)
            .apply(oauth2Client.oauth2Configuration())
            .baseUrl(BASE_URL)
            .build();
    }

    public SpringDiscordClient getDiscordClient()
    {
        return discordClient;
    }

    private void readRequestRate(ClientResponse response)
    {
        response.headers().header(RATE_LIMIT_LIMIT_HEADER).stream()
            .findAny()
            .ifPresent(rpsStr->{
                int rps = Math.min(Integer.parseInt(rpsStr), MAX_REQUESTS_PER_SECOND);
                requestsPerSecond.set(rps);
                LOG.trace("Request per second cap(from headers): {}", rps);
            });
    }

    private <T> Mono<T> readRequestRateAndExchangeToMono(ClientResponse response, Class<T> clazz)
    {
        readRequestRate(response);
        return response.bodyToMono(clazz);
    }

    private <T> Flux<T> readRequestRateAndExchangeToFlux(ClientResponse response, Class<T> clazz)
    {
        readRequestRate(response);
        return response.bodyToFlux(clazz);
    }

    private <T> Mono<T> getMono(Class<T> clazz, String uri, Object... params)
    {
        return client
            .get()
            .uri(uri, params)
            .attributes(clientRegistrationId(USER_CLIENT_REGISTRATION_ID))
            .accept(ALL)
            .exchangeToMono(resp->readRequestRateAndExchangeToMono(resp, clazz))
            .retryWhen(rateLimiter.retryWhen(RETRY_WHEN_TOO_MANY_REQUESTS))
            .delaySubscription(rateLimiter.requestSlot());
    }

    private <T> Flux<T> getFlux(Class<T> clazz, String uri, Object... params)
    {
        return client
            .get()
            .uri(uri, params)
            .attributes(clientRegistrationId(USER_CLIENT_REGISTRATION_ID))
            .accept(ALL)
            .exchangeToFlux(resp->readRequestRateAndExchangeToFlux(resp, clazz))
            .retryWhen(rateLimiter.retryWhen(RETRY_WHEN_TOO_MANY_REQUESTS))
            .delaySubscription(rateLimiter.requestSlot());
    }

    public Mono<DiscordUser> getCurrentUser()
    {
        return getMono(DiscordUser.class, "/users/@me");
    }

    public Mono<DiscordUser> getUser(Long id)
    {
        return discordClient.getClient()
            .getUserById(Snowflake.of(id))
            .map(DiscordUser::from)
            .delaySubscription(rateLimiter.requestSlot());
    }

    public Flux<DiscordUser> getUsers(Iterable<? extends Long> ids)
    {
        return Flux.fromIterable(ids)
            .flatMap(id->WebServiceUtil.getOnErrorLogAndSkipMono(getUser(id)));
    }

    public Flux<DiscordConnection> getCurrentUserConnections()
    {
        return getFlux(DiscordConnection.class, "/users/@me/connections")
            .delaySubscription(rateLimiter.requestSlot());
    }

    /**
     * Use this method with care. Discord and users do not like unsolicited DMs. Send DM only
     * to users who subscribed to them or for important events like security related messages.
     *
     * @param dm DM text
     * @param ids Discord ids of recipients
     * @return Flux of sent messages
     */
    public Flux<Message> sendDM(String dm, Long... ids)
    {
        return Flux.fromArray(ids)
            .map(Snowflake::of)
            .flatMap(discordClient.getClient()::getUserById)
            .flatMap(User::getPrivateChannel)
            .flatMap(c->c.createMessage(dm));
    }

    public Mono<Void> updateConnectionMetaData(List<ConnectionMetaData> data)
    {
        return client
            .put()
            .uri("/applications/{applicationId}/role-connections/metadata", applicationId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(data)
            .accept(ALL)
            .header("Authorization", "Bot " + token)
            .exchangeToMono(resp->readRequestRateAndExchangeToMono(resp, Void.class))
            .retryWhen(rateLimiter.retryWhen(RETRY_WHEN_TOO_MANY_REQUESTS))
            .delaySubscription(rateLimiter.requestSlot());
    }

    public Mono<Void> updateConnectionMetaData
    (
        String principalName,
        ApplicationRoleConnection connection
    )
    {
        OAuth2AuthorizedClient oAuth2AuthorizedClient = auth2AuthorizedClientService
            .loadAuthorizedClient(USER_CLIENT_REGISTRATION_ID, principalName);
        if(oAuth2AuthorizedClient == null) return Mono
            .error(new IllegalStateException("OAuth2AuthorizedClient not found for user " + principalName));

        return client
            .put()
            .uri("/users/@me/applications/{application.id}/role-connection", applicationId)
            .contentType(MediaType.APPLICATION_JSON)
            .attributes(oauth2AuthorizedClient(oAuth2AuthorizedClient))
            .bodyValue(connection)
            .accept(ALL)
            .exchangeToMono(resp->readRequestRateAndExchangeToMono(resp, Void.class))
            .retryWhen(rateLimiter.retryWhen(RETRY_WHEN_TOO_MANY_REQUESTS))
            .delaySubscription(rateLimiter.requestSlot());
    }

}
