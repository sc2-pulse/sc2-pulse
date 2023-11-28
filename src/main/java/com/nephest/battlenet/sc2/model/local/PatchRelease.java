// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.Region;
import java.time.OffsetDateTime;
import java.util.Objects;
import javax.validation.constraints.NotNull;

public class PatchRelease
{

    @NotNull
    private Long patchBuild;

    @NotNull
    private Region region;

    @NotNull
    private OffsetDateTime released;

    public PatchRelease()
    {
    }

    public PatchRelease(Long patchBuild, Region region, OffsetDateTime released)
    {
        this.patchBuild = patchBuild;
        this.region = region;
        this.released = released;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof PatchRelease)) {return false;}
        PatchRelease that = (PatchRelease) o;
        return Objects.equals(patchBuild, that.patchBuild) && region == that.region;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(patchBuild, region);
    }

    @Override
    public String toString()
    {
        return "PatchRelease{" + "patchBuild=" + patchBuild + ", region=" + region + '}';
    }

    public Long getPatchBuild()
    {
        return patchBuild;
    }

    public void setPatchBuild(Long patchBuild)
    {
        this.patchBuild = patchBuild;
    }

    public Region getRegion()
    {
        return region;
    }

    public void setRegion(Region region)
    {
        this.region = region;
    }

    public OffsetDateTime getReleased()
    {
        return released;
    }

    public void setReleased(OffsetDateTime released)
    {
        this.released = released;
    }

}
