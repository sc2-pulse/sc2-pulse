// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.discord.Discord;
import com.nephest.battlenet.sc2.web.service.DiscordService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Discord
@Component
public class UpdateDataAuthenticationSuccessHandler
extends SavedRequestAwareAuthenticationSuccessHandler
{

    public static final String DEFAULT_SUCCESS_URL = "/?#personal-characters";

    private final DiscordService discordService;

    @Autowired
    public UpdateDataAuthenticationSuccessHandler(DiscordService discordService)
    {
        super.setDefaultTargetUrl(DEFAULT_SUCCESS_URL);
        this.discordService = discordService;
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
            updateOauth2Data((OAuth2AuthenticationToken) authentication);
        super.onAuthenticationSuccess(request, response, authentication);
    }


    private void updateOauth2Data(OAuth2AuthenticationToken token)
    {
        if(!(token.getPrincipal() instanceof AccountOauth2User<? extends OAuth2User> user)) return;

        if(token.getAuthorizedClientRegistrationId().equals("discord-lg"))
            updateDiscordOauth2Data(user);
    }

    private void updateDiscordOauth2Data(AccountOauth2User<? extends OAuth2User> user)
    {
        discordService.updateRoles(user.getAccount().getId()).blockLast();
    }

}
