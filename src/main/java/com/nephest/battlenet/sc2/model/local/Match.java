// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardMatch;

import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Objects;

public class Match
extends BaseMatch
implements java.io.Serializable
{

    private static final long serialVersionUID = 2L;

    private Long id;

    @NotNull
    private Region region;

    @NotNull
    private OffsetDateTime updated = OffsetDateTime.now();

    public Match(){}

    public Match(Long id, @NotNull OffsetDateTime date, @NotNull MatchType type, @NotNull String map, @NotNull Region region)
    {
        super(date, type, map);
        this.id = id;
        this.region = region;
    }

    public static Match of(BlizzardMatch match, Region region)
    {
        return new Match(null, match.getDate(), match.getType(), match.getMap(), region);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Match otherMatch = (Match) o;
        return getDate().isEqual(otherMatch.getDate())
            && getType() == otherMatch.getType()
            && getMap().equals(otherMatch.getMap())
            && getRegion() == otherMatch.getRegion();
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getDate().toEpochSecond(), getType(), getMap(), getRegion());
    }

    @Override
    public String toString()
    {
        return String.format("%s[%s %s %s %s]", Match.class.getSimpleName(), getDate(), getType(), getMap(), getRegion());
    }

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Region getRegion()
    {
        return region;
    }

    public void setRegion(Region region)
    {
        this.region = region;
    }

    public OffsetDateTime getUpdated()
    {
        return updated;
    }

    public void setUpdated(OffsetDateTime updated)
    {
        this.updated = updated;
    }

}
