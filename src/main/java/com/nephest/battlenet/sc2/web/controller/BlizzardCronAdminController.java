// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.blizzard.Blizzard;
import com.nephest.battlenet.sc2.web.service.BlizzardCron;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@Hidden
@Blizzard
@ConditionalOnProperty(name = "com.nephest.battlenet.sc2.cron.enabled", havingValue = "true")
public class BlizzardCronAdminController
{

    @Autowired
    private BlizzardCron blizzardCron;

    @PostMapping("/update/ladder/{update}")
    public void setUpdateLadder(@PathVariable("update") Boolean updateLadder)
    {
        blizzardCron.setShouldUpdateLadder(updateLadder);
    }

}
