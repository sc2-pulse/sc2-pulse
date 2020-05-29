// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLeagueTier;

import javax.validation.constraints.NotNull;
import java.util.Objects;

public class LeagueTier
extends BaseLeagueTier
implements java.io.Serializable
{

    private static final long serialVersionUID = 1L;

    private Long id;

    @NotNull
    private Long leagueId;

    public LeagueTier(){}

    public LeagueTier(Long id, Long leagueId, LeagueTierType type, Integer minRating, Integer maxRating)
    {
        super(type, minRating, maxRating);
        this.id = id;
        this.leagueId = leagueId;
    }

    public static LeagueTier of(League league, BlizzardLeagueTier bTier)
    {
        return new LeagueTier
        (
            null,
            league.getId(),
            bTier.getType(),
            bTier.getMinRating(),
            bTier.getMaxRating()
        );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getLeagueId(), getType());
    }

    @Override
    public boolean equals(Object other)
    {
        if (other == null) return false;
        if (other == this) return true;
        if ( !(other instanceof LeagueTier) ) return false;

        LeagueTier otherTier = (LeagueTier) other;
        return getLeagueId().equals(otherTier.getLeagueId())
            && getType() == otherTier.getType();
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s]",
            getClass().getSimpleName(),
            getLeagueId(), getType().toString()
        );
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }

    public void setLeagueId(Long leagueId)
    {
        this.leagueId = leagueId;
    }

    public Long getLeagueId()
    {
        return leagueId;
    }

}
