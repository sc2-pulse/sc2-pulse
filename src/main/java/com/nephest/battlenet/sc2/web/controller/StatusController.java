// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.web.service.StatsService;
import com.nephest.battlenet.sc2.web.service.UpdateContext;
import com.nephest.battlenet.sc2.web.service.UpdateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@Profile({"!maintenance"})
@RestController
@RequestMapping("/api/status")
public class StatusController
{

    @Autowired
    private StatsService statsService;

    @Autowired
    private UpdateService updateService;

    @GetMapping("/stale")
    public Set<Region> getStaleStatus()
    {
        return statsService.getAlternativeRegions();
    }

    @GetMapping("/stale/forced")
    public Set<Region> getForcedStaleStatus()
    {
        return statsService.getForcedAlternativeRegions();
    }

    @GetMapping("/updated")
    public UpdateContext updated()
    {
        return updateService.getUpdateContext(null);
    }

}
