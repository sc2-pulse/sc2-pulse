// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.config.security.DiscordAuthorizationRequestResolver;
import com.nephest.battlenet.sc2.config.security.DiscordOauth2State;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller @Hidden
@RequestMapping("/verify")
public class VerificationController
{

    @Autowired @Qualifier("mvcConversionService")
    ConversionService mvcConversionService;

    @GetMapping("/discord")
    public String verifyDiscord()
    {
        return "redirect:/oauth2/authorization/discord-lg";
    }

    @GetMapping("/discord/linked-roles")
    public String verifyDiscordLinkedRoles()
    {
        return "redirect:/oauth2/authorization/discord-lg"
            + "?" + DiscordAuthorizationRequestResolver.FLAG_PARAMETER_NAME
            + "=" + mvcConversionService.convert(DiscordOauth2State.Flag.LINKED_ROLE, String.class);
    }

}
