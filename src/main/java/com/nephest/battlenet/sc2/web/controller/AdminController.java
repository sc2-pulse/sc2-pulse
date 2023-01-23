// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.config.Cron;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.web.service.AlternativeLadderService;
import com.nephest.battlenet.sc2.web.service.BlizzardSC2API;
import com.nephest.battlenet.sc2.web.service.MatchService;
import com.nephest.battlenet.sc2.web.service.StatsService;
import com.nephest.battlenet.sc2.web.service.SupporterService;
import io.swagger.v3.oas.annotations.Hidden;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @Autowired
    private SupporterService supporterService;

    //lazy for tests
    @Autowired @Lazy
    private Cron cron;

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

    @PostMapping("/supporters/supporter/{name}")
    public void addSupporter(@PathVariable String name)
    {
        supporterService.addSupporter(name);
    }

    @DeleteMapping("/supporters/supporter/{name}")
    public void removeSupporter(@PathVariable String name)
    {
        supporterService.removeSupporter(name);
    }

    @PostMapping("/supporters/donor/{name}")
    public void addDonor(@PathVariable String name)
    {
        supporterService.getDonorsVar().getValue().add(name);
        supporterService.getDonorsVar().save();
    }

    @DeleteMapping("/supporters/donor/{name}")
    public void removeDonor(@PathVariable String name)
    {
        supporterService.getDonorsVar().getValue().remove(name);
        supporterService.getDonorsVar().save();
    }

    @PostMapping("/update/ladder/{update}")
    public void setUpdateLadder(@PathVariable("update") Boolean updateLadder)
    {
        cron.setShouldUpdateLadder(updateLadder);
    }

    @PostMapping("/blizzard/api/timeout/{region}/{timeout}")
    public void setTimeout
    (
        @PathVariable("region") Region region,
        @PathVariable("timeout") int timeout
    )
    {
        sc2API.setTimeout(region, Duration.ofMillis(timeout));
    }

    @DeleteMapping("/blizzard/api/timeout/{region}")
    public void removeTimeout(@PathVariable("region") Region region)
    {
        sc2API.setTimeout(region, null);
    }



}
