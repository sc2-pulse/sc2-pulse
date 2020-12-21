// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardMatch;

import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Objects;

public class Match
extends BaseMatch
implements java.io.Serializable
{

    private static final long serialVersionUID = 1L;

    private Long id;

    @NotNull
    private OffsetDateTime updated = OffsetDateTime.now();

    public Match(){}

    public Match(Long id, @NotNull OffsetDateTime date, @NotNull MatchType type, @NotNull String map)
    {
        super(date, type, map);
        this.id = id;
    }

    public static Match of(BlizzardMatch match)
    {
        return new Match(null, match.getDate(), match.getType(), match.getMap());
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseMatch baseMatch = (BaseMatch) o;
        return getDate().isEqual(baseMatch.getDate())
            && getType() == baseMatch.getType()
            && getMap().equals(baseMatch.getMap());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getDate(), getType(), getMap());
    }

    @Override
    public String toString()
    {
        return String.format("%s[%s %s %s]", Match.class.getSimpleName(), getDate(), getType(), getMap());
    }

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
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
