// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.web.service.StatsService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
@Hidden
public class AdminController
{

    @Autowired
    private StatsService statsService;

    @PostMapping("/alternative/forced/{region}")
    public void addForcedAlternativeRegion(@PathVariable("region") Region region)
    {
        statsService.addForcedAlternativeRegion(region);
    }

    @DeleteMapping("/alternative/forced/{region}")
    public void removeForcedAlternativeRegion(@PathVariable("region") Region region)
    {
        statsService.removeForcedAlternativeRegion(region);
    }

}
