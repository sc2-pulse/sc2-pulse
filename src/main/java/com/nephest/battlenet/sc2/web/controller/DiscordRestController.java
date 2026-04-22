// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.config.security.AccountUser;
import com.nephest.battlenet.sc2.discord.Discord;
import com.nephest.battlenet.sc2.web.service.DiscordService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@Discord
@RestController
@RequestMapping("/api/my/discord")
public class DiscordRestController
{

    @Autowired
    private DiscordService discordService;

    @PostMapping("/unlink")
    public void unlinkDiscordUser(@AuthenticationPrincipal AccountUser user)
    {
        discordService.unlinkAccountFromDiscordUser(user.getAccount().getId(), null);
    }

    @PostMapping("/public/{public}")
    public void setPublicFlag
    (
        @AuthenticationPrincipal AccountUser user,
        @PathVariable("public") Boolean isPublic
    )
    {
        discordService.setVisibility(user.getAccount().getId(), isPublic);
    }

}
