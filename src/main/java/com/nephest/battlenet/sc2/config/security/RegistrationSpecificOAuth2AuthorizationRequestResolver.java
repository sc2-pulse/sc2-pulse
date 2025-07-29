// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;

public interface RegistrationSpecificOAuth2AuthorizationRequestResolver
extends OAuth2AuthorizationRequestResolver
{

    String getClientRegistrationId();

}
