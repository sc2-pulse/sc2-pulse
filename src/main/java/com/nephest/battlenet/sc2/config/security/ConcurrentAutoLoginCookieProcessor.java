// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.model.local.AuthenticationRequest;
import com.nephest.battlenet.sc2.model.local.dao.AuthenticationRequestDAO;
import java.time.Duration;
import java.time.OffsetDateTime;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
    <p>
        This service uses auth cache to handle burst auto logins which prevents remember me token
        invalidation when serving multiple API calls concurrently. Successful authentications are
        cached and reused when subsequent auth requests are made using the same series/token
        combination if cached entry is still alive(TTL). When cached authentication is used, then
        all token and cookie operations are skipped, but the general auth flow remains the same
        (find series/token->find user).
    </p>
    <p>
        Authentication cache is persisted via the DB. This is sufficient if there are no other
        app instances or when other instances are temporal(app or config update, maintenance,
        etc.). Use a separate service(like Redis) for caching, persisting, and locking if you want
        to run multiple app instances.
    </p>
 */
@Service @Lazy
public class ConcurrentAutoLoginCookieProcessor
implements AutoLoginCookieProcessor
{

    private static final Logger LOG = LoggerFactory
        .getLogger(ConcurrentAutoLoginCookieProcessor.class);

    public static final Duration AUTH_CACHE_TTL = Duration.ofSeconds(60);

    private final AutoLoginCookieProcessor realProcessor;
    private final UserDetailsService userDetailsService;
    private final PersistentTokenRepository persistentTokenRepository;
    private final AuthenticationRequestDAO authenticationRequestDAO;

    private final Object AUTH_CACHE_LOCK = new Object();
    private boolean autoClean = true;

    @Autowired
    public ConcurrentAutoLoginCookieProcessor
    (
        @Qualifier("concurrentPersistentTokenBasedRememberMeService") AutoLoginCookieProcessor realProcessor,
        UserDetailsService userDetailsService,
        PersistentTokenRepository tokenRepository,
        AuthenticationRequestDAO authenticationRequestDAO
    )
    {
        this.realProcessor = realProcessor;
        this.userDetailsService = userDetailsService;
        this.persistentTokenRepository = tokenRepository;
        this.authenticationRequestDAO = authenticationRequestDAO;
    }

    @Retryable
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Override
    public UserDetails doProcessAutoLoginCookie
    (
        String[] cookieTokens,
        HttpServletRequest request,
        HttpServletResponse response
    )
    {
        if(cookieTokens.length != 2)
            return realProcessor.doProcessAutoLoginCookie(cookieTokens, request, response);

        synchronized (AUTH_CACHE_LOCK)
        {
            if(isAutoClean()) removeExpired();

            String series = cookieTokens[0];
            String authRequest = series + cookieTokens[1];
            if(authenticationRequestDAO.exists(authRequest, AUTH_CACHE_TTL))
            {
                PersistentRememberMeToken token = persistentTokenRepository
                    .getTokenForSeries(series);
                if (token == null) throw new RememberMeAuthenticationException
                    ("No persistent token found for series id: " + series);

                LOG.debug("Used authentication cache for user {}, series {}",
                    token.getUsername(), series);
                return userDetailsService.loadUserByUsername(token.getUsername());
            }
            else
            {
                UserDetails userDetails = realProcessor.doProcessAutoLoginCookie(cookieTokens, request, response);
                if(userDetails == null) throw new IllegalStateException("Unexpected null value");

                createAuthenticationRequest(authRequest);
                return userDetails;
            }
        }
    }

    private void createAuthenticationRequest(String name)
    {
        authenticationRequestDAO.merge(new AuthenticationRequest(name, OffsetDateTime.now()));
    }

    public boolean isAutoClean()
    {
        return autoClean;
    }

    public void setAutoClean(boolean autoClean)
    {
        this.autoClean = autoClean;
    }

    public void removeExpired()
    {
        synchronized (AUTH_CACHE_LOCK)
        {
            authenticationRequestDAO.removeExpired();
        }
    }

}
