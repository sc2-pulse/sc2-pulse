// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/*
    This class synchronizes concurrent auto logins and reuses the authentication object for subsequent calls,
    which prevents remember me token invalidation when serving multiple API calls concurrently.
 */

public class ConcurrentPersistentTokenBasedRememberMeService
implements RememberMeServices
{

    public static final int AUTH_CACHE_TTL_SECONDS = 60;

    private final Object AUTH_CACHE_LOCK = new Object();

    private final Map<String, Tuple2<Authentication, Instant>> AUTH_CACHE = new HashMap<>();

    private boolean autoClean = true;

    private final PersistentTokenBasedRememberMeServices persistentTokenBasedRememberMeServices;

    public ConcurrentPersistentTokenBasedRememberMeService
    (PersistentTokenBasedRememberMeServices persistentTokenBasedRememberMeServices)
    {
        this.persistentTokenBasedRememberMeServices = persistentTokenBasedRememberMeServices;
    }

    @Override
    public Authentication autoLogin
    (javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse response)
    {
        synchronized (AUTH_CACHE_LOCK)
        {
            String cookie = request.getCookies() == null ? null : Arrays.stream(request.getCookies())
                .filter(c->c.getName().equals(SecurityConfig.REMEMBER_ME_COOKIE_NAME))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
            if(cookie == null) return persistentTokenBasedRememberMeServices.autoLogin(request, response);

            if(isAutoClean()) removeExpired();
            Tuple2<Authentication, Instant> authentication = AUTH_CACHE.get(cookie);
            if (authentication != null && authentication.getT2().isAfter(Instant.now().minusSeconds(AUTH_CACHE_TTL_SECONDS)))
                return authentication.getT1();

            Authentication newAuth = persistentTokenBasedRememberMeServices.autoLogin(request, response);
            AUTH_CACHE.put(cookie, Tuples.of(newAuth, Instant.now()));
            return newAuth;
        }
    }

    @Override
    public void loginFail(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
    {
        persistentTokenBasedRememberMeServices.loginFail(httpServletRequest, httpServletResponse);
    }

    @Override
    public void loginSuccess
    (HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Authentication authentication)
    {
        persistentTokenBasedRememberMeServices.loginSuccess(httpServletRequest, httpServletResponse, authentication);
    }

    public boolean isAutoClean()
    {
        return autoClean;
    }

    public void setAutoClean(boolean autoClean)
    {
        this.autoClean = autoClean;
    }

    public String getKey()
    {
        return persistentTokenBasedRememberMeServices.getKey();
    }

    public void removeExpired()
    {
        synchronized (AUTH_CACHE_LOCK)
        {
            Instant to = Instant.now().minusSeconds(AUTH_CACHE_TTL_SECONDS);
            String[] expired = AUTH_CACHE.entrySet().stream()
                .filter(e->e.getValue().getT2().isBefore(to))
                .map(Map.Entry::getKey)
                .toArray(String[]::new);
            for(String key : expired) AUTH_CACHE.remove(key);
        }
    }

}
