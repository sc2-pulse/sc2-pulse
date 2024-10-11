// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import java.util.Set;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

/**
 * This class is useful when there are several OAuth2 registrations, but only some of them should
 * be considered fully authenticated while others should be considered remembered.
 */
public class OAuthRegistrationAuthenticationTrustResolver
implements AuthenticationTrustResolver
{

    private final AuthenticationTrustResolver resolver;
    private final Set<String> registrationIds;

    public OAuthRegistrationAuthenticationTrustResolver
    (
        AuthenticationTrustResolver resolver,
        Set<String> registrationIds
    )
    {
        this.registrationIds = registrationIds;
        this.resolver = resolver;
    }

    @Override
    public boolean isAnonymous(Authentication authentication)
    {
        return resolver.isAnonymous(authentication);
    }

    /**
     *
     * @param authentication authentication
     * @return <p>For {@link OAuth2AuthenticationToken} {@link Authentication},
     * true if {@link #registrationIds}
     * don't contain {@link OAuth2AuthenticationToken#getAuthorizedClientRegistrationId()}</p>
     * <p>Other cases: supplied {@link #resolver} is used</p>
     */
    @Override
    public boolean isRememberMe(Authentication authentication)
    {
        if(!(authentication instanceof OAuth2AuthenticationToken))
            return getResolver().isRememberMe(authentication);

        return !getRegistrationIds()
            .contains(((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId())
                || getResolver().isRememberMe(authentication);
    }

    public AuthenticationTrustResolver getResolver()
    {
        return resolver;
    }

    public Set<String> getRegistrationIds()
    {
        return registrationIds;
    }

}
