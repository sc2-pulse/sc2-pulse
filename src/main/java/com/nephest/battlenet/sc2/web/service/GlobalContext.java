// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.Region;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GlobalContext
{

    private final Set<Region> activeRegions;

    @Autowired
    public GlobalContext
    (
        @Value("${com.nephest.battlenet.sc2.ladder.regions}") Set<Region> activeRegions
    )
    {
        this.activeRegions = activeRegions;
    }

    public Set<Region> getActiveRegions()
    {
        return activeRegions;
    }

}
