// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.liquipedia;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.web.service.liquipedia.LiquipediaParser;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

public class LiquipediaPatch
{

    private final Long build;
    private final String version;
    private final Map<Region, LocalDate> releases;
    private final Boolean versus;

    public LiquipediaPatch
    (
        Long build,
        String version,
        Map<Region, LocalDate> releases,
        Boolean versus
    )
    {
        this.build = build;
        this.version = version;
        this.releases = releases;
        this.versus = versus;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof LiquipediaPatch)) {return false;}
        LiquipediaPatch patch = (LiquipediaPatch) o;
        return Objects.equals(getBuild(), patch.getBuild());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getBuild());
    }

    public Long getBuild()
    {
        return build;
    }

    public String getVersion()
    {
        return version;
    }

    public Map<Region, LocalDate> getReleases()
    {
        return releases;
    }

    public Boolean isVersus()
    {
        return versus;
    }

    public boolean isBalanceUpdate()
    {
        return LiquipediaParser.isBalanceUpdateVersion(getVersion());
    }

}
