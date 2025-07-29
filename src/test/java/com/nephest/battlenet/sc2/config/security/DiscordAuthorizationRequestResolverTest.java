// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.keygen.BytesKeyGenerator;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

@ExtendWith(MockitoExtension.class)
public class DiscordAuthorizationRequestResolverTest
{

    private static final ConversionService CONVERSION_SERVICE = new DefaultConversionService();

    @Mock
    private OAuth2AuthorizationRequestResolver defaultResolver;

    @Mock
    private BytesKeyGenerator keyGenerator;
    private final byte[] key = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06};

    private DiscordAuthorizationRequestResolver resolver;

    @BeforeEach
    public void beforeEach()
    {
        lenient().when(keyGenerator.generateKey()).thenReturn(key);
        lenient().when(keyGenerator.getKeyLength()).thenReturn(key.length);
        resolver = new DiscordAuthorizationRequestResolver
        (
            defaultResolver,
            CONVERSION_SERVICE,
            keyGenerator
        );
    }

    public static Stream<Arguments> testResolve()
    {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        for(DiscordOauth2State.Flag flag : DiscordOauth2State.Flag.values())
            httpRequest.addParameter
            (
                DiscordAuthorizationRequestResolver.FLAG_PARAMETER_NAME,
                CONVERSION_SERVICE.convert(flag, String.class)
            );

        return Stream.of
        (
            Arguments.of(httpRequest, "AQIDBAUGAQ=="),
            Arguments.of(new MockHttpServletRequest(), "AQIDBAUG")
        );
    }

    @MethodSource
    @ParameterizedTest
    public void testResolve
    (
        HttpServletRequest httpRequest,
        String state
    )
    {
        OAuth2AuthorizationRequest defaultOauthRequest = SecurityTestUtil.oauth2Request();
        when(defaultResolver.resolve(httpRequest)).thenReturn(defaultOauthRequest);
        OAuth2AuthorizationRequest resolvedRequest = resolver.resolve(httpRequest);

        assertEquals(defaultOauthRequest.getClientId(), resolvedRequest.getClientId());
        assertEquals(state, resolvedRequest.getState());

        String registrationId = "regId";
        when(defaultResolver.resolve(httpRequest, registrationId)).thenReturn(defaultOauthRequest);
        OAuth2AuthorizationRequest resolvedRequest2 = resolver.resolve(httpRequest, registrationId);
        assertEquals(state, resolvedRequest2.getState());
    }

    @Test
    public void whenDefaultResolverReturnsNull_thenReturnNull()
    {
        HttpServletRequest httpRequest = new MockHttpServletRequest();
        assertNull(resolver.resolve(httpRequest));
        assertNull(resolver.resolve(httpRequest, "registrationId"));
    }

    @Test
    public void testGetKeyLength()
    {
        assertEquals(key.length, resolver.getKeyLength());
    }

}
