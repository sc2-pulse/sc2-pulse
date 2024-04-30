// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.RefreshTokenOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.core.OAuth2Token;

/**
 * This provider caches successful refresh requests and reuses them in subsequent requests to
 * eliminate a race condition when 2 requests with the same refresh token are made which can lead
 * to a situation where subsequent requests are denied by the resource server and then
 * invalidated by Spring.
 */
public class CachedRefreshTokenOAuth2AuthorizedClientProvider
implements OAuth2AuthorizedClientProvider
{

    private static final Logger LOG =
        LoggerFactory.getLogger(CachedRefreshTokenOAuth2AuthorizedClientProvider.class);

    public static final Duration CACHE_TTL = Duration.ofSeconds(60);
    public static final Duration CLOCK_SKEW = Duration.ofSeconds(60);
    public static final Clock CLOCK = Clock.systemUTC();

    private final Map<RefreshTokenIdentity, Pair<OAuth2AuthorizedClient, Instant>> cache =
        new HashMap<>();

    private final RefreshTokenOAuth2AuthorizedClientProvider realProvider;

    public CachedRefreshTokenOAuth2AuthorizedClientProvider
    (
        RefreshTokenOAuth2AuthorizedClientProvider realProvider
    )
    {
        this.realProvider = realProvider;
        realProvider.setClock(CLOCK);
        realProvider.setClockSkew(CLOCK_SKEW);
    }

    public CachedRefreshTokenOAuth2AuthorizedClientProvider()
    {
        this(new RefreshTokenOAuth2AuthorizedClientProvider());
    }

    /*TODO
        This uses a simple in-memory cache and lock. This is a quick fix of an active bug and
        should be replaced with a proper external cache and lock method(i.e. Redis).
        The current method is sufficient as long as there is only **one app instance** that can send
        refresh token requests.
     */
    @Override
    public OAuth2AuthorizedClient authorize(OAuth2AuthorizationContext context)
    {
        return canAuthorize(context) ? doAuthorize(context) : null;
    }

    private synchronized OAuth2AuthorizedClient doAuthorize(OAuth2AuthorizationContext context)
    {
        LOG.trace("Received refresh token request for {}", context.getPrincipal().getName());
        clearCache();
        RefreshTokenIdentity id = RefreshTokenIdentity.from(context);
        if(id == null) return authorizeAndCache(context);

        Pair<OAuth2AuthorizedClient, Instant> cachedClient = cache.get(id);
        if(cachedClient != null)
        {
            LOG.debug("Used cached refresh token request for {}", context.getPrincipal().getName());
            return cachedClient.getKey();
        }

        return authorizeAndCache(context);
    }

    public boolean canAuthorize(OAuth2AuthorizationContext context)
    {
        OAuth2AuthorizedClient authorizedClient = context.getAuthorizedClient();
        return authorizedClient != null
            && authorizedClient.getRefreshToken() != null
            && hasTokenExpired(authorizedClient.getAccessToken());
    }

    private boolean hasTokenExpired(OAuth2Token token) {
        return CLOCK.instant().isAfter(token.getExpiresAt().minus(CLOCK_SKEW));
    }

    private OAuth2AuthorizedClient authorizeAndCache(OAuth2AuthorizationContext context)
    {
        OAuth2AuthorizedClient auth = realProvider.authorize(context);
        if(auth != null)
        {
            cache.put
            (
                RefreshTokenIdentity.from(context),
                new ImmutablePair<>(auth, SC2Pulse.instant())
            );
            LOG.debug("Cached refresh token request for {}", context.getPrincipal().getName());
        }
        return auth;
    }

    private void clearCache()
    {
        Instant minInstant = SC2Pulse.instant().minus(CACHE_TTL);
        cache.entrySet().removeIf(e->e.getValue().getValue().isBefore(minInstant));
    }

}
