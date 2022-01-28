// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.web.service.AlternativeLadderService;
import com.nephest.battlenet.sc2.web.service.BlizzardSC2API;
import com.nephest.battlenet.sc2.web.service.MatchService;
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
    private AlternativeLadderService alternativeLadderService;

    @Autowired
    private BlizzardSC2API sc2API;

    @Autowired
    private MatchService matchService;

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

    @PostMapping("/alternative/profile/ladder/web/region/{region}")
    public void addAlternativeProfileLadderWebRegion(@PathVariable("region") Region region)
    {
        alternativeLadderService.addProfileLadderWebRegion(region);
    }

    @DeleteMapping("/alternative/profile/ladder/web/region/{region}")
    public void removeAlternativeProfileLadderWebRegion(@PathVariable("region") Region region)
    {
        alternativeLadderService.removeProfileLadderWebRegion(region);
    }

    @PostMapping("/alternative/discovery/web/region/{region}")
    public void addAlternativeWebRegion(@PathVariable("region") Region region)
    {
        alternativeLadderService.addDiscoveryWebRegion(region);
    }

    @DeleteMapping("/alternative/discovery/web/region/{region}")
    public void removeAlternativeWebRegion(@PathVariable("region") Region region)
    {
        alternativeLadderService.removeDiscoveryWebRegion(region);
    }

    @PostMapping("/blizzard/api/region/{region}/force/{force}")
    public void addForceAPIRegion(@PathVariable("region") Region region, @PathVariable("force") Region force)
    {
        sc2API.setForceRegion(region, force);
    }

    @PostMapping("/blizzard/api/force/region/auto/{auto}")
    public void setAutoForceRegion(@PathVariable("auto") boolean auto)
    {
        sc2API.setAutoForceRegion(auto);
    }

    @DeleteMapping("/blizzard/api/region/{region}/force")
    public void removeForceAPIRegion(@PathVariable("region") Region region)
    {
        sc2API.setForceRegion(region, null);
    }

    @PostMapping("/blizzard/api/match/web/region/{region}")
    public void addMatchWebRegion(@PathVariable("region") Region region)
    {
        matchService.addWebRegion(region);
    }

    @DeleteMapping("/blizzard/api/match/web/region/{region}")
    public void removeMatchWebRegion(@PathVariable("region") Region region)
    {
        matchService.removeWebRegion(region);
    }

}
