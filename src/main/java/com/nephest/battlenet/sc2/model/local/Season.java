// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.BaseSeason;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardSeason;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Objects;

public class Season
extends BaseSeason
implements java.io.Serializable
{

    private static final long serialVersionUID = 5L;

    private Integer id;

    @NotNull
    private Integer battlenetId;

    /**
     * Canonical season key for (year, number), see pulse_season table. It's
     * equal to battlenetId in most cases, but region-local Blizzard ids can
     * differ, e.g. CN. Null until the season is persisted.
     */
    private Integer pulseSeasonId;

    @NotNull
    private Region region;

    public Season(){}

    public Season
    (
        Integer id,
        Integer battlenetId,
        Integer pulseSeasonId,
        Region region,
        Integer year,
        Integer number,
        OffsetDateTime start,
        OffsetDateTime end
    )
    {
        super(year, number, start, end);
        this.id = id;
        this.battlenetId = battlenetId;
        this.pulseSeasonId = pulseSeasonId;
        this.region = region;
    }

    /**
     * Creates a season with an unresolved pulse season id. The id is resolved
     * when the season is persisted.
     */
    public Season
    (
        Integer id,
        Integer battlenetId,
        Region region,
        Integer year,
        Integer number,
        OffsetDateTime start,
        OffsetDateTime end
    )
    {
        this(id, battlenetId, null, region, year, number, start, end);
    }

    public static Season of(BlizzardSeason season, Region region)
    {
        return new Season
        (
            null,
            season.getId(),
            region,
            season.getYear(),
            season.getNumber(),
            season.getStart(),
            season.getEnd()
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
        if (!(other instanceof Season otherSeason)) return false;

        return getBattlenetId().equals(otherSeason.getBattlenetId())
            && getRegion() == otherSeason.getRegion();
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s]",
            Season.class.getSimpleName(),
            getBattlenetId(), getRegion().toString()
        );
    }

    public void setId(Integer id)
    {
        this.id = id;
    }

    public Integer getId()
    {
        return id;
    }

    public void setBattlenetId(Integer battlenetId)
    {
        this.battlenetId = battlenetId;
    }

    public Integer getBattlenetId()
    {
        return battlenetId;
    }

    public void setPulseSeasonId(Integer pulseSeasonId)
    {
        this.pulseSeasonId = pulseSeasonId;
    }

    public Integer getPulseSeasonId()
    {
        return pulseSeasonId;
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
