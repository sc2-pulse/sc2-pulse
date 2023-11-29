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
    private Integer patchId;

    @NotNull
    private Region region;

    @NotNull
    private OffsetDateTime released;

    public PatchRelease()
    {
    }

    public PatchRelease(Integer patchId, Region region, OffsetDateTime released)
    {
        this.patchId = patchId;
        this.region = region;
        this.released = released;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof PatchRelease)) {return false;}
        PatchRelease that = (PatchRelease) o;
        return Objects.equals(patchId, that.patchId) && region == that.region;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(patchId, region);
    }

    @Override
    public String toString()
    {
        return "PatchRelease{" + "patchId=" + patchId + ", region=" + region + '}';
    }

    public Integer getPatchId()
    {
        return patchId;
    }

    public void setPatchId(Integer patchId)
    {
        this.patchId = patchId;
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
