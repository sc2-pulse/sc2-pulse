// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import java.util.Set;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

/**
 * This {@link AuthorizationManager} grants access only to selected OAuth registrations. It is
 * useful in combination with {@link OAuthRegistrationAuthenticationTrustResolver} when there are
 * several OAuth2 registrations, but only some of them should be considered fully authenticated
 * while others should be considered remembered. Non-OAuth authentications are delegated to
 * supplied {@link #authorizationManager}
 * @param <T>
 */
public class OAuthRegistrationAuthorizationManager<T>
implements AuthorizationManager<T>
{

    private final AuthorizationManager<T> authorizationManager;
    private final Set<String> registrationIds;

    public OAuthRegistrationAuthorizationManager
    (
        AuthorizationManager<T> authorizationManager,
        Set<String> registrationIds
    )
    {
        this.authorizationManager = authorizationManager;
        this.registrationIds = registrationIds;
    }

    private AuthorizationDecision checkRegistration
    (
        Supplier<Authentication> authenticationSupplier,
        AuthorizationDecision originalDecision
    )
    {
        Authentication authentication = authenticationSupplier.get();
        if(!(authentication instanceof OAuth2AuthenticationToken)) return originalDecision;

        boolean granted = getRegistrationIds()
            .contains(((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId());
        return new AuthorizationDecision(granted);
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, T object)
    {
        AuthorizationDecision decision = getAuthorizationManager()
            .check(authentication, object);
        if(decision != null && !decision.isGranted()) return decision;

        return checkRegistration(authentication, decision);
    }

    public AuthorizationManager<T> getAuthorizationManager()
    {
        return authorizationManager;
    }

    public Set<String> getRegistrationIds()
    {
        return registrationIds;
    }

}
