// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Patch;
import java.time.OffsetDateTime;
import java.util.Map;

public class LadderPatch
{

    private final Patch patch;
    private final Map<Region, OffsetDateTime> releases;

    public LadderPatch(Patch patch, Map<Region, OffsetDateTime> releases)
    {
        this.patch = patch;
        this.releases = releases;
    }

    public Patch getPatch()
    {
        return patch;
    }

    public Map<Region, OffsetDateTime> getReleases()
    {
        return releases;
    }

}
