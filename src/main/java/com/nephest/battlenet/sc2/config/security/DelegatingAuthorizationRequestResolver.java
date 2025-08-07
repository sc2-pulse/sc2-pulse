// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;

@Component
public class DelegatingAuthorizationRequestResolver
implements OAuth2AuthorizationRequestResolver
{

    private final OAuth2AuthorizationRequestResolver defaultResolver;
    private final Map<String, OAuth2AuthorizationRequestResolver> customizers;

    @Autowired
    public DelegatingAuthorizationRequestResolver
    (
        @Qualifier("defaultOAuth2AuthorizationRequestResolver")
        OAuth2AuthorizationRequestResolver defaultResolver,
        List<RegistrationSpecificOAuth2AuthorizationRequestResolver> customizers
    )
    {
        this.defaultResolver = defaultResolver;
        this.customizers = customizers.stream()
            .collect(Collectors.toMap(
                RegistrationSpecificOAuth2AuthorizationRequestResolver::getClientRegistrationId,
                Function.identity()
            ));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request)
    {
        OAuth2AuthorizationRequest req = defaultResolver.resolve(request);
        if(req == null) return null;

        String clientRegistrationId = (String) req.getAttributes()
            .get(OAuth2ParameterNames.REGISTRATION_ID);
        if(clientRegistrationId == null) return req;

        OAuth2AuthorizationRequestResolver customizer = customizers.get(clientRegistrationId);
        return customizer != null ? customizer.resolve(request, clientRegistrationId) : req;
    }

    @Override
    public OAuth2AuthorizationRequest resolve
    (
        HttpServletRequest request,
        String clientRegistrationId
    )
    {
        return customizers.getOrDefault(clientRegistrationId, defaultResolver)
            .resolve(request, clientRegistrationId);
    }

}