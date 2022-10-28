// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller @Hidden
public class ShortURLController
{

    @Value("${discord.bot.invite.url}")
    private String discordBotInviteUrl;

    @GetMapping("/discord/bot/invite")
    public String inviteDiscordBot()
    {
        return "redirect:" + discordBotInviteUrl;
    }

}
