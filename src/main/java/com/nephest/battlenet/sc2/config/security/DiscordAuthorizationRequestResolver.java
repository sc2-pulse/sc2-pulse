// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.discord.Discord;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.security.crypto.keygen.BytesKeyGenerator;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

@Component @Discord
public class DiscordAuthorizationRequestResolver
implements RegistrationSpecificOAuth2AuthorizationRequestResolver
{

    public static final String FLAG_PARAMETER_NAME = "flag";
    public static final int DEFAULT_KEY_LENGTH = 32;

    private final OAuth2AuthorizationRequestResolver defaultResolver;
    private final ConversionService conversionService;
    private final BytesKeyGenerator bytesKeyGenerator;

    public DiscordAuthorizationRequestResolver
    (
        OAuth2AuthorizationRequestResolver defaultResolver,
        ConversionService conversionService,
        BytesKeyGenerator bytesKeyGenerator
    )
    {
        this.defaultResolver = defaultResolver;
        this.conversionService = conversionService;
        this.bytesKeyGenerator = bytesKeyGenerator;
    }

    @Autowired
    public DiscordAuthorizationRequestResolver
    (
        @Qualifier("defaultOAuth2AuthorizationRequestResolver")
        OAuth2AuthorizationRequestResolver defaultResolver,
        @Qualifier("mvcConversionService") ConversionService conversionService
    )
    {
        this(defaultResolver, conversionService, KeyGenerators.secureRandom(DEFAULT_KEY_LENGTH));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request)
    {
        OAuth2AuthorizationRequest oauthReq = defaultResolver.resolve(request);
        if(oauthReq != null) oauthReq = customizeAuthorizationRequest(request, oauthReq);

        return oauthReq;
    }

    @Override
    public OAuth2AuthorizationRequest resolve
    (
        HttpServletRequest request,
        String clientRegistrationId
    )
    {
        OAuth2AuthorizationRequest oauthReq = defaultResolver.resolve(request, clientRegistrationId);
        if(oauthReq != null) oauthReq = customizeAuthorizationRequest(request, oauthReq);

        return oauthReq;
    }

    @Override
    public String getClientRegistrationId()
    {
        return "discord-lg";
    }

    private OAuth2AuthorizationRequest customizeAuthorizationRequest
    (
        HttpServletRequest httpReq,
        OAuth2AuthorizationRequest oauthReq
    )
    {
        return OAuth2AuthorizationRequest.from(oauthReq)
            .state(createLinkedRoleState(httpReq).toUriString())
            .build();
    }

    private DiscordOauth2State createLinkedRoleState
    (
        HttpServletRequest httpReq
    )
    {
        return new DiscordOauth2State
        (
            bytesKeyGenerator.generateKey(),
            Optional.ofNullable(httpReq.getParameterValues(FLAG_PARAMETER_NAME))
                .map(params->Arrays.stream(params)
                    .map(v->conversionService.convert(v, DiscordOauth2State.Flag.class))
                    .collect(Collectors.toSet()))
                .orElse(Set.of())
        );
    }

    public int getKeyLength()
    {
        return bytesKeyGenerator.getKeyLength();
    }

}
