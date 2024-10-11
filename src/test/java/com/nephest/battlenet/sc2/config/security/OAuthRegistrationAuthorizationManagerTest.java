// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

@ExtendWith(MockitoExtension.class)
public class OAuthRegistrationAuthorizationManagerTest
{

    private static final Set<String> ALLOWED_REGISTRATION_IDS = Set.of("allowed1", "allowed2");

    @Mock
    private AuthorizationManager<?> upstreamManager;

    private OAuthRegistrationAuthorizationManager<?> manager;

    @BeforeEach
    public void beforeEach()
    {
        manager = new OAuthRegistrationAuthorizationManager<>
        (
            upstreamManager,
            ALLOWED_REGISTRATION_IDS
        );
    }

    @CsvSource
    ({
        "allowed1, true, true",
        "allowed2, true, true",
        "allowed1, false, false",
        "allowed2, false, false",
        "notAllowed, false, false",
        "notAllowed, true, false",
        ", true, true",
        ", false, false"
    })
    @ParameterizedTest
    public void testCheck(String clientId, boolean originalDecision, boolean expectedDecision)
    {
        lenient().when(upstreamManager.check(any(), any()))
            .thenReturn(new AuthorizationDecision(originalDecision));
        Authentication authentication = createAuthentication(clientId);
        AuthorizationDecision decision = manager.check(()->authentication, null);
        assertNotNull(decision);
        assertEquals(expectedDecision, decision.isGranted());
    }

    public static Authentication createAuthentication(String clientId)
    {
        if(clientId != null)
        {
            OAuth2AuthenticationToken oAuth2AuthenticationToken
                = mock(OAuth2AuthenticationToken.class);
            lenient().when(oAuth2AuthenticationToken.getAuthorizedClientRegistrationId())
                .thenReturn(clientId);
            return oAuth2AuthenticationToken;
        }
        return mock(Authentication.class);
    }

}
