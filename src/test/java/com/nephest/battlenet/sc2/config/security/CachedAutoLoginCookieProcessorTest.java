// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.local.dao.AuthenticationRequestDAO;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

@ExtendWith(MockitoExtension.class)
public class CachedAutoLoginCookieProcessorTest
{

    @Mock
    private AutoLoginCookieProcessor realProcessor;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private PersistentTokenRepository persistentTokenRepository;

    @Mock
    private AuthenticationRequestDAO authenticationRequestDAO;

    private CachedAutoLoginCookieProcessor processor;

    @BeforeEach
    public void beforeEach()
    {
        processor = new CachedAutoLoginCookieProcessor
        (
            realProcessor,
            userDetailsService,
            persistentTokenRepository,
            authenticationRequestDAO
        );
    }

    @Test
    public void whenAuthenticationFailed_thenDontCacheIt()
    {
        when(authenticationRequestDAO.exists(any(), any())).thenReturn(false);
        when(realProcessor.doProcessAutoLoginCookie(any(), any(), any()))
            .thenThrow(new IllegalStateException("test"));
        processor.setAutoClean(false);

        try
        {
            processor.doProcessAutoLoginCookie
            (
                new String[]{"series", "token"},
                mock(HttpServletRequest.class),
                mock(HttpServletResponse.class)
            );
            fail("Expected exception not thrown");
        }
        catch (Exception ignored){}

        verifyNoMoreInteractions(authenticationRequestDAO);
    }

    @Test
    public void whenAuthenticationSucceeded_thenCacheIt()
    {
        when(authenticationRequestDAO.exists(any(), any())).thenReturn(false);
        when(realProcessor.doProcessAutoLoginCookie(any(), any(), any()))
            .thenReturn(new User("name", "pwd", List.of()));
        processor.setAutoClean(false);

        OffsetDateTime beforeRun = OffsetDateTime.now();
        processor.doProcessAutoLoginCookie
        (
            new String[]{"series", "token"},
            mock(HttpServletRequest.class),
            mock(HttpServletResponse.class)
        );

        verify(authenticationRequestDAO)
            .merge(argThat(r->r.getName().equals("seriestoken") && r.getCreated().isAfter(beforeRun)));
    }

    @Test
    public void whenCachedAuthenticationIsAvailable_thenUseIt()
    {
        when(authenticationRequestDAO.exists(any(), any())).thenReturn(true);
        when(persistentTokenRepository.getTokenForSeries("series"))
            .thenReturn(new PersistentRememberMeToken("name", "series", "token", Date.from(Instant.now())));
        UserDetails ud = new User("name", "pwd", List.of());
        when(userDetailsService.loadUserByUsername("name")).thenReturn(ud);
        processor.setAutoClean(false);

        UserDetails processedDetails = processor.doProcessAutoLoginCookie
        (
            new String[]{"series", "token"},
            mock(HttpServletRequest.class),
            mock(HttpServletResponse.class)
        );

        assertEquals(ud, processedDetails);
        verify(authenticationRequestDAO).exists(any(), any());
        verifyNoMoreInteractions(authenticationRequestDAO);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    public void testAutoClean(boolean autoClean)
    {
        when(authenticationRequestDAO.exists(any(), any())).thenReturn(false);
        when(realProcessor.doProcessAutoLoginCookie(any(), any(), any()))
            .thenReturn(new User("name", "pwd", List.of()));
        processor.setAutoClean(autoClean);

        processor.doProcessAutoLoginCookie
        (
            new String[]{"series", "token"},
            mock(HttpServletRequest.class),
            mock(HttpServletResponse.class)
        );

        if(autoClean)
        {
            verify(authenticationRequestDAO).removeExpired();
        }
        else
        {
            verify(authenticationRequestDAO, never()).removeExpired();
        }
    }


}
