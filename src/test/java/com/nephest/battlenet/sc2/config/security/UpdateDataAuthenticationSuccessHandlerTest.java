// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.web.service.DiscordService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
public class UpdateDataAuthenticationSuccessHandlerTest
{

    private static final int KEY_LENGTH = 2;

    @Mock
    private DiscordService discordService;

    @Mock
    private ViewResolver viewResolver;

    private UpdateDataAuthenticationSuccessHandler handler;

    @BeforeEach
    public void beforeEach()
    {
        handler = new UpdateDataAuthenticationSuccessHandler
        (
            discordService,
            KEY_LENGTH,
            viewResolver
        );
    }

    @CsvSource
    ({
        "AQIB, true",
        "AQI=, false"
    })
    @ParameterizedTest
    public void testDiscordLinkedRolesResultForwarding
    (
        String state,
        boolean forward
    )
    throws Exception
    {
        when(discordService.updateRoles(1L)).thenReturn(Flux.empty());

        View view = mock(View.class);
        lenient().when(viewResolver.resolveViewName(
                eq(UpdateDataAuthenticationSuccessHandler.ACCOUNT_VERIFICATION_VIEW_NAME),
                any()))
            .thenReturn(view);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("state", state);
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthenticationToken token = new OAuth2AuthenticationToken
        (
            new AccountOauth2User<>
            (
                new DefaultOAuth2User(List.of(), Map.of("sub", "123"), "sub"),
                new Account(1L, Partition.GLOBAL, "tag#1234"),
                "password",
                List.of(SC2PulseAuthority.USER)
            ),
            List.of(),
            "discord-lg"
        );

        handler.onAuthenticationSuccess(request, response, token);

        assertEquals
        (
            forward ? HttpStatus.OK.value() : HttpStatus.FOUND.value(),
            response.getStatus()
        );
        if(!forward) assertEquals
        (
            UpdateDataAuthenticationSuccessHandler.DEFAULT_SUCCESS_URL,
            response.getHeader(HttpHeaders.LOCATION)
        );
        verify(view, times(forward ? 1 : 0))
            .render(Map.of("statusCode", response.getStatus()), request, response);
    }

}
