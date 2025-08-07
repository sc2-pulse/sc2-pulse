// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

public final class SecurityTestUtil
{

    private SecurityTestUtil(){}

    public static OAuth2AuthorizationRequest.Builder oauth2RequestBuilder()
    {
        return OAuth2AuthorizationRequest
            .authorizationCode()
            .authorizationUri("http://127.0.0.1")
            .clientId("id");
    }

    public static OAuth2AuthorizationRequest oauth2Request()
    {
        return oauth2RequestBuilder().build();
    }

}
