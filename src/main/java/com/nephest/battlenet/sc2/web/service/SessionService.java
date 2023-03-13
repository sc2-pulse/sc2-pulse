// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.config.security.CachedAutoLoginCookieProcessor;
import com.nephest.battlenet.sc2.config.security.ConcurrentPersistentTokenBasedRememberMeService;
import com.nephest.battlenet.sc2.config.security.SecurityConfig;
import com.nephest.battlenet.sc2.model.local.dao.AuthenticationRequestDAO;
import java.util.Arrays;
import java.util.Objects;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Service;

@Service
public class SessionService
{

    private static final Logger LOG = LoggerFactory.getLogger(SessionService.class);

    private final AuthenticationRequestDAO authenticationRequestDAO;
    private final ConcurrentPersistentTokenBasedRememberMeService rememberMeService;
    private final PersistentTokenRepository tokenRepository;

    @Autowired
    public SessionService
    (
        AuthenticationRequestDAO authenticationRequestDAO,
        ConcurrentPersistentTokenBasedRememberMeService rememberMeService,
        PersistentTokenRepository tokenRepository
    )
    {
        this.authenticationRequestDAO = authenticationRequestDAO;
        this.rememberMeService = rememberMeService;
        this.tokenRepository = tokenRepository;
    }

    /**
     * Sets currently active remember-me cookie for a given series if supplied cookie(from request)
     * is stale but is still in {@link com.nephest.battlenet.sc2.model.local.dao.AuthenticationRequestDAO}
     * cache. Can be useful if remember-me tokens were used to create a new session, but client
     * didn't receive the updated tokens for some reason.
     * @param user user
     * @param request request
     * @param response response
     */
    public void setLastRememberMeCookie
    (
        UserDetails user,
        HttpServletRequest request,
        HttpServletResponse response
    )
    {
        Arrays.stream(request.getCookies())
            .filter(c->c.getName().equals(SecurityConfig.REMEMBER_ME_COOKIE_NAME))
            .map(Cookie::getValue)
            .map(rememberMeService::decodeCookie)
            .filter
            (
                tokens->authenticationRequestDAO.exists
                (
                    String.join("", tokens),
                    CachedAutoLoginCookieProcessor.AUTH_CACHE_TTL
                )
            )
            //ix 0 is series
            .map(tokens->tokenRepository.getTokenForSeries(tokens[0]))
            .filter(Objects::nonNull)
            .filter(token->token.getUsername().equals(user.getUsername()))
            .forEach
            (
                token->
                {
                    rememberMeService.setCookie
                    (
                        new String[]{token.getSeries(), token.getTokenValue()},
                        (int) SecurityConfig.REMEMBER_ME_DURATION
                            .minus(CachedAutoLoginCookieProcessor.AUTH_CACHE_TTL)
                            .toSeconds(),
                        request,
                        response
                    );
                    LOG.info("Synced remember-me tokens for {}", user.getUsername());
                }
            );
    }

}
