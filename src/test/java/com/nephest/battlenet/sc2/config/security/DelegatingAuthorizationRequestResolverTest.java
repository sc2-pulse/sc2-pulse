// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

@ExtendWith(MockitoExtension.class)
public class DelegatingAuthorizationRequestResolverTest
{

    private static final String CUSTOMIZER_CLIENT_REGISTRATION_ID = "customize";

    @Mock
    private OAuth2AuthorizationRequestResolver defaultResolver;

    @Mock
    private RegistrationSpecificOAuth2AuthorizationRequestResolver customizer;

    private DelegatingAuthorizationRequestResolver resolver;

    @BeforeEach
    public void beforeEach()
    {
        when(customizer.getClientRegistrationId()).thenReturn(CUSTOMIZER_CLIENT_REGISTRATION_ID);
        resolver = new DelegatingAuthorizationRequestResolver(defaultResolver, List.of(customizer));
    }

    @Test
    public void whenClientRegistrationIdIsProvidedAndMatchesCustomizer_thenCustomize()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        OAuth2AuthorizationRequest oauthRequest = SecurityTestUtil.oauth2Request();
        when(customizer.resolve(request, CUSTOMIZER_CLIENT_REGISTRATION_ID))
            .thenReturn(oauthRequest);

        assertEquals(oauthRequest, resolver.resolve(request, CUSTOMIZER_CLIENT_REGISTRATION_ID));
        verify(defaultResolver, never()).resolve(any(), anyString());
    }

    @Test
    public void whenClientRegistrationIdIsProvidedAndDoesNotMatchCustomizer_thenDontCustomize()
    {
        String clientRegistrationId = CUSTOMIZER_CLIENT_REGISTRATION_ID + "a";
        MockHttpServletRequest request = new MockHttpServletRequest();
        OAuth2AuthorizationRequest oauthRequest = SecurityTestUtil.oauth2Request();
        when(defaultResolver.resolve(request, clientRegistrationId))
            .thenReturn(oauthRequest);
        assertEquals(oauthRequest, resolver.resolve(request, clientRegistrationId));
        verify(customizer, never()).resolve(any(), anyString());
    }

    @Test
    public void whenClientRegistrationIdIsNotProvidedAndResolvedIdMatchesCustomizer_thenCustomize()
    {
        OAuth2AuthorizationRequest oauthRequest = SecurityTestUtil.oauth2RequestBuilder()
            .attributes(Map.of(
                OAuth2ParameterNames.REGISTRATION_ID, CUSTOMIZER_CLIENT_REGISTRATION_ID))
            .build();
        OAuth2AuthorizationRequest customizedOauthRequest = OAuth2AuthorizationRequest.from(oauthRequest)
            .state("state")
            .build();
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(defaultResolver.resolve(request)).thenReturn(oauthRequest);
        when(customizer.resolve(request, CUSTOMIZER_CLIENT_REGISTRATION_ID))
            .thenReturn(customizedOauthRequest);
        assertEquals(customizedOauthRequest, resolver.resolve(request));
    }

    @Test
    public void whenClientRegistrationIdIsNotProvidedAndResolvedRequestIsNull_thenReturnNull()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(defaultResolver.resolve(request)).thenReturn(null);
        assertNull(resolver.resolve(request));
        verify(customizer, never()).resolve(any(), anyString());
    }

    @Test
    public void whenClientRegistrationIdIsNotProvidedAndResolvedIdIsNull_thenReturnResolvedRequest()
    {
        OAuth2AuthorizationRequest oauthRequest = SecurityTestUtil.oauth2Request();
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(defaultResolver.resolve(request)).thenReturn(oauthRequest);
        assertEquals(oauthRequest, resolver.resolve(request));
        verify(customizer, never()).resolve(any(), anyString());
    }

    @Test
    public void whenClientRegistrationIdIsNotProvidedAndResolvedIdDoesNotMatchCustomizer_thenReturnResolvedRequest()
    {
        OAuth2AuthorizationRequest oauthRequest = SecurityTestUtil.oauth2RequestBuilder()
            .attributes(Map.of(
                OAuth2ParameterNames.REGISTRATION_ID,
                CUSTOMIZER_CLIENT_REGISTRATION_ID + "a"))
            .build();
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(defaultResolver.resolve(request)).thenReturn(oauthRequest);
        assertEquals(oauthRequest, resolver.resolve(request));
        verify(customizer, never()).resolve(any(), anyString());
    }

}
