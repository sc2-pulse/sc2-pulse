// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.discord.Discord;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Discord
@Controller
@Hidden
@ConditionalOnProperty(name = "com.nephest.battlenet.sc2.discord.bot.invite.url")
public class DiscordBotUrlController
{

    @Value("${com.nephest.battlenet.sc2.discord.bot.invite.url}")
    private String discordBotInviteUrl;

    @GetMapping("/discord/bot/invite")
    public String inviteDiscordBot()
    {
        return "redirect:" + discordBotInviteUrl;
    }

}
