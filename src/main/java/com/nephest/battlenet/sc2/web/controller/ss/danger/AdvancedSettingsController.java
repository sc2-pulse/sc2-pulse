// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller.ss.danger;

import com.nephest.battlenet.sc2.config.security.AccountUser;
import com.nephest.battlenet.sc2.web.service.AccountService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@Hidden
@RequestMapping("/settings/advanced")
public class AdvancedSettingsController
{

    @Autowired
    private AccountService accountService;


    @GetMapping
    public String render()
    {
        return "advanced-settings";
    }

    @RequestMapping
    (
        method = {RequestMethod.POST, RequestMethod.DELETE},
        params = {"action=invalidate-sessions"}
    )
    public String invalidateAllSessions(@AuthenticationPrincipal AccountUser user)
    {
        accountService.invalidateSessions(user.getAccount().getId());
        return "redirect:/";
    }

}
