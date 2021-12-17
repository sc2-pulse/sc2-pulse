// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.web.service.BlizzardSC2API;
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

    @Autowired
    private BlizzardSC2API sc2API;

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

    @PostMapping("/blizzard/api/region/{region}/force/{force}")
    public void addForceAPIRegion(@PathVariable("region") Region region, @PathVariable("force") Region force)
    {
        sc2API.setForceRegion(region, force);
    }

    @DeleteMapping("/blizzard/api/region/{region}/force")
    public void removeForceAPIRegion(@PathVariable("region") Region region)
    {
        sc2API.setForceRegion(region, null);
    }

}
