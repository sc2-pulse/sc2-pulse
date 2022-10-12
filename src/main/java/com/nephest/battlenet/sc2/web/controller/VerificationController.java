// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller @Hidden
@RequestMapping("/verify")
public class VerificationController
{

    @GetMapping("/discord")
    public String verifyDiscord()
    {
        return "redirect:/oauth2/authorization/discord-lg";
    }

}
