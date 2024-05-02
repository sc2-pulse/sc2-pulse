// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Hidden @Controller @HtmlController
public class CoreHtmlController
{

    @GetMapping("/")
    public String home()
    {
        return "home";
    }

    @GetMapping("/login")
    public String login()
    {
        return "plogin";
    }

    @GetMapping("/terms-of-service")
    public String termsOfService()
    {
        return "terms-of-service";
    }

    @GetMapping("/privacy-policy")
    public String privacyPolicy()
    {
        return "privacy-policy";
    }

    @GetMapping("/about")
    public String about()
    {
        return "about";
    }

    @GetMapping("/status")
    public String status()
    {
        return "status";
    }

    @GetMapping("/contacts")
    public String contacts()
    {
        return "contacts";
    }

    @GetMapping("/donate")
    public String donate()
    {
        return "donate";
    }

    @GetMapping("/team/history")
    public String teamHistory()
    {
        return "team-history";
    }

    @GetMapping("/discord/bot")
    public String discordBot()
    {
        return "discord-bot";
    }

}
