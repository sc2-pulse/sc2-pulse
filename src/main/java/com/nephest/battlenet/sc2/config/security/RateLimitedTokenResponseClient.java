// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.endpoint.AbstractOAuth2AuthorizationGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import reactor.core.publisher.Mono;

public class RateLimitedTokenResponseClient<T extends AbstractOAuth2AuthorizationGrantRequest>
implements OAuth2AccessTokenResponseClient<T>
{

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitedTokenResponseClient.class);

    private final OAuth2AccessTokenResponseClient<T> client;
    private final Map<String, OAuth2RateLimiter> rateLimiters;


    public RateLimitedTokenResponseClient
    (
        OAuth2AccessTokenResponseClient<T> client,
        Map<String, OAuth2RateLimiter> rateLimiters
    )
    {
        this.client = client;
        this.rateLimiters = rateLimiters;
    }

    public RateLimitedTokenResponseClient
    (
        OAuth2AccessTokenResponseClient<T> client,
        List<OAuth2RateLimiter> rateLimiters
    )
    {
        this(client, mapRateLimiters(rateLimiters));
    }

    public static Map<String, OAuth2RateLimiter> mapRateLimiters(List<OAuth2RateLimiter> rateLimiters)
    {
        return rateLimiters.stream().collect(Collectors.toUnmodifiableMap(
            OAuth2RateLimiter::getClientRegistrationId, Function.identity()));
    }

    @Override
    public OAuth2AccessTokenResponse getTokenResponse
    (
        T authorizationGrantRequest
    )
    {
        LOG.trace
        (
            "Received token request {} {}",
            authorizationGrantRequest.getClientRegistration().getRegistrationId(),
            authorizationGrantRequest.getGrantType().getValue()
        );
        OAuth2RateLimiter limiter = rateLimiters
            .get(authorizationGrantRequest.getClientRegistration().getRegistrationId());
        if(limiter != null)
        {
            LOG.trace
            (
                "Using rate limiter for {} {}",
                authorizationGrantRequest.getClientRegistration().getRegistrationId(),
                authorizationGrantRequest.getGrantType().getValue()
            );
            return limiter
                .withLimiter(Mono.fromCallable(()->client.getTokenResponse(authorizationGrantRequest)), false)
                .blockLast();
        }
        LOG.trace
        (
            "Rate limiter not found for {} {}, using the token client without the rate limit.",
            authorizationGrantRequest.getClientRegistration().getRegistrationId(),
            authorizationGrantRequest.getGrantType().getValue()
        );
        return client.getTokenResponse(authorizationGrantRequest);
    }

}
