// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.BaseSeason;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardSeason;

import javax.validation.constraints.NotNull;
import java.util.Objects;

public class Season
extends BaseSeason
implements java.io.Serializable
{

    private static final long serialVersionUID = 1L;

    private Long id;

    @NotNull
    private Long battlenetId;

    @NotNull
    private Region region;

    public Season(){}

    public Season
    (
        Long id,
        Long battlenetId,
        Region region,
        Integer year,
        Integer number
    )
    {
        super(year, number);
        this.id = id;
        this.battlenetId = battlenetId;
        this.region = region;
    }

    public static Season of(BlizzardSeason season, Region region)
    {
        return new Season
        (
            null,
            season.getId(),
            region,
            season.getYear(),
            season.getNumber()
        );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getBattlenetId(), getRegion());
    }

    @Override
    public boolean equals(Object other)
    {
        if (other == null) return false;
        if (this == other) return true;
        if (!(other instanceof Season)) return false;

        Season otherSeason = (Season) other;
        return getBattlenetId().equals(otherSeason.getBattlenetId())
            && getRegion() == otherSeason.getRegion();
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s]",
            getClass().getSimpleName(),
            getBattlenetId(), getRegion().toString()
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

    public void setBattlenetId(Long battlenetId)
    {
        this.battlenetId = battlenetId;
    }

    public Long getBattlenetId()
    {
        return battlenetId;
    }

    public void setRegion(Region region)
    {
        this.region = region;
    }

    public Region getRegion()
    {
        return region;
    }

}
