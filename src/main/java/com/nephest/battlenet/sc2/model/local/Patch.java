// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.blizzard.cache.BlizzardCachePatch;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class Patch
implements java.io.Serializable
{

    private static final long serialVersionUID = 2L;

    Integer id;

    @NotNull
    public Long build;

    @NotNull
    private String version;

    private Boolean versus;

    public Patch()
    {
    }

    public Patch(Integer id, Long build, String version, Boolean versus)
    {
        this.id = id;
        this.build = build;
        this.version = version;
        this.versus = versus;
    }

    public Patch(Long build, String version, Boolean versus)
    {
        this.build = build;
        this.version = version;
        this.versus = versus;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof Patch patch)) {return false;}
        return Objects.equals(getBuild(), patch.getBuild())
            && Objects.equals(getVersion(), patch.getVersion());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getBuild(), getVersion());
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s]",
            Patch.class.getSimpleName(),
            getBuild(),
            getVersion()
        );
    }

    public static Patch from(BlizzardCachePatch patch)
    {
        return new Patch(patch.getBuildNumber(), patch.getVersion(), null);
    }

    public Integer getId()
    {
        return id;
    }

    public void setId(Integer id)
    {
        this.id = id;
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

    public Boolean getVersus()
    {
        return versus;
    }

    public void setVersus(Boolean versus)
    {
        this.versus = versus;
    }

}
