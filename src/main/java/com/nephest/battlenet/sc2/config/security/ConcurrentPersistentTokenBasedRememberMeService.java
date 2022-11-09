// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Service;

/**
 * Synchronized wrapper for token based remember me services. Implementation is split between this
 * class and {@link ConcurrentAutoLoginCookieProcessor} because some methods in
 * {@link org.springframework.security.web.authentication.rememberme.AbstractRememberMeServices}
 * are final which makes it impossible to wrap it into Spring proxies.
 */
@Service
public class ConcurrentPersistentTokenBasedRememberMeService
extends PersistentTokenBasedRememberMeServices
implements AutoLoginCookieProcessor
{

    public static final String REMEMBER_ME_KEY_PROPERTY_NAME = "security.remember-me.token.key";

    private final AutoLoginCookieProcessor concurrentCookieProcessor;

    @Autowired
    public ConcurrentPersistentTokenBasedRememberMeService
    (
        @Value("${" + REMEMBER_ME_KEY_PROPERTY_NAME + ":dev}") String key,
        UserDetailsService userDetailsService,
        PersistentTokenRepository tokenRepository,
        Environment environment,
        @Lazy @Qualifier("concurrentAutoLoginCookieProcessor") AutoLoginCookieProcessor concurrentCookieProcessor
    )
    {
        super(key, userDetailsService, tokenRepository);
        Set<String> activeProfiles = Set.of(environment.getActiveProfiles());
        if
        (
            !activeProfiles.contains("test")
            && activeProfiles.contains("prod")
            && key.equals("dev")
        )
            throw new IllegalStateException("Static key(" + REMEMBER_ME_KEY_PROPERTY_NAME + ") "
                + "must be provided when prod profile is active");
        this.concurrentCookieProcessor = concurrentCookieProcessor;
        setAlwaysRemember(true);
        setTokenValiditySeconds((int) SecurityConfig.REMEMBER_ME_DURATION.toSeconds());
        setCookieName(SecurityConfig.REMEMBER_ME_COOKIE_NAME);
    }

    @Override
    public UserDetails processAutoLoginCookie
    (
        String[] cookieTokens,
        HttpServletRequest request,
        HttpServletResponse response
    )
    {
        return concurrentCookieProcessor.doProcessAutoLoginCookie(cookieTokens, request, response);
    }

    @Override
    public UserDetails doProcessAutoLoginCookie
    (
        String[] cookieTokens,
        HttpServletRequest request,
        HttpServletResponse response
    )
    {
        return super.processAutoLoginCookie(cookieTokens, request, response);
    }

}
