// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.blizzard.cache.BlizzardCachePatch;
import java.time.OffsetDateTime;
import java.util.Objects;
import javax.validation.constraints.NotNull;

public class Patch
implements java.io.Serializable
{

    private static final long serialVersionUID = 1L;

    @NotNull
    public Long build;

    @NotNull
    private String version;

    @NotNull
    private OffsetDateTime published;

    public Patch()
    {
    }

    public Patch(Long build, String version, OffsetDateTime published)
    {
        this.build = build;
        this.version = version;
        this.published = published;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof Patch)) {return false;}
        Patch patch = (Patch) o;
        return Objects.equals(getBuild(), patch.getBuild());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getBuild());
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s]",
            Patch.class.getSimpleName(),
            getBuild()
        );
    }

    public static Patch from(BlizzardCachePatch patch)
    {
        return new Patch(patch.getBuildNumber(), patch.getVersion(), patch.getPublish());
    }

    public Long getBuild()
    {
        return build;
    }

    public void setBuild(Long build)
    {
        this.build = build;
    }

    public String getVersion()
    {
        return version;
    }

    public void setVersion(String version)
    {
        this.version = version;
    }

    public OffsetDateTime getPublished()
    {
        return published;
    }

    public void setPublished(OffsetDateTime published)
    {
        this.published = published;
    }

}
