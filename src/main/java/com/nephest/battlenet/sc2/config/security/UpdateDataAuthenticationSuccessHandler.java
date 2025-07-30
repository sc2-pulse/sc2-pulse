// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.discord.Discord;
import com.nephest.battlenet.sc2.web.service.DiscordService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ViewResolver;

@Discord
@Component
public class UpdateDataAuthenticationSuccessHandler
extends SavedRequestAwareAuthenticationSuccessHandler
{

    public static final String DEFAULT_SUCCESS_URL = "/?#personal-characters";
    public static final String ACCOUNT_VERIFICATION_VIEW_NAME = "account-verification-result";

    private final DiscordService discordService;
    private final int discordStateKeyLength;
    private final ViewResolver viewResolver;

    @Autowired
    public UpdateDataAuthenticationSuccessHandler
    (
        DiscordService discordService,
        @Value("#{discordAuthorizationRequestResolver.getKeyLength()}") int discordStateKeyLength,
        ViewResolver viewResolver
    )
    {
        super.setDefaultTargetUrl(DEFAULT_SUCCESS_URL);
        this.discordService = discordService;
        this.discordStateKeyLength = discordStateKeyLength;
        this.viewResolver = viewResolver;
    }

    @Override
    public void onAuthenticationSuccess
    (
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    )
    throws ServletException, IOException
    {
        if(authentication instanceof OAuth2AuthenticationToken)
            updateOauth2Data(request, response, (OAuth2AuthenticationToken) authentication);
        super.onAuthenticationSuccess(request, response, authentication);
    }


    private void updateOauth2Data
    (
        HttpServletRequest request,
        HttpServletResponse response,
        OAuth2AuthenticationToken token
    )
    throws ServletException, IOException
    {
        if(!(token.getPrincipal() instanceof AccountOauth2User<? extends OAuth2User> user)) return;

        if(token.getAuthorizedClientRegistrationId().equals("discord-lg"))
            handleDiscordOauthData(request, response, user);

    }

    private void handleDiscordOauthData
    (
        HttpServletRequest request,
        HttpServletResponse response,
        AccountOauth2User<? extends OAuth2User> user
    )
    throws ServletException, IOException
    {
        updateDiscordOauth2Data(user);
        handleDiscordOauthState(request, response);
    }

    private void handleDiscordOauthState
    (
        HttpServletRequest request,
        HttpServletResponse response
    )
    throws ServletException, IOException
    {
        String rawState = request.getParameter("state");
        if(rawState == null) return;

        DiscordOauth2State state = DiscordOauth2State
            .fromUrlString(rawState, discordStateKeyLength);
        if(!state.getFlags().contains(DiscordOauth2State.Flag.LINKED_ROLE)) return;

        try
        {
            viewResolver.resolveViewName(ACCOUNT_VERIFICATION_VIEW_NAME, request.getLocale())
                .render(Map.of("statusCode", response.getStatus()), request, response);
            response.flushBuffer();
        }
        catch (Exception e)
        {
            throw new ServletException(e);
        }
    }

    private void updateDiscordOauth2Data(AccountOauth2User<? extends OAuth2User> user)
    {
        discordService.updateRoles(user.getAccount().getId()).blockLast();
    }

}
