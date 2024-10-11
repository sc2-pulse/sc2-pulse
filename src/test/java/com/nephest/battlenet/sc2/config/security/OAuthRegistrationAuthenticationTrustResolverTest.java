// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import static com.nephest.battlenet.sc2.config.security.OAuthRegistrationAuthorizationManagerTest.createAuthentication;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
public class OAuthRegistrationAuthenticationTrustResolverTest
{

    private static final Set<String> ALLOWED_REGISTRATION_IDS = Set.of("allowed1", "allowed2");

    @Mock
    private AuthenticationTrustResolver upstreamResolver;

    private OAuthRegistrationAuthenticationTrustResolver resolver;

    @BeforeEach
    public void beforeEach()
    {
        resolver = new OAuthRegistrationAuthenticationTrustResolver
        (
            upstreamResolver,
            ALLOWED_REGISTRATION_IDS
        );
    }

    @CsvSource
    ({
        "allowed1, true, true",
        "allowed2, true, true",
        "allowed1, false, false",
        "allowed2, false, false",
        "notAllowed, true, true",
        "notAllowed, false, true",
        ", true, true",
        ", false, false"
    })
    @ParameterizedTest
    public void testRememberMe(String clientId, boolean originalDecision, boolean expectedDecision)
    {
        Authentication authentication = createAuthentication(clientId);
        lenient().when(upstreamResolver.isRememberMe(any())).thenReturn(originalDecision);
        boolean decision = resolver.isRememberMe(authentication);
        assertEquals(expectedDecision, decision);
    }

}
