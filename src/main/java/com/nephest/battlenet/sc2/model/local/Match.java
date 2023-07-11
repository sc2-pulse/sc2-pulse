// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardMatch;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Objects;
import javax.validation.constraints.NotNull;

public class Match
extends BaseMatch
implements java.io.Serializable
{

    private static final long serialVersionUID = 3L;

    public static final Comparator<Match> NATURAL_ID_COMPARATOR =
        Comparator.comparing(Match::getDate)
            .thenComparing(Match::getType)
            .thenComparing(Match::getMapId)
            .thenComparing(Match::getRegion);

    private Long id;

    @NotNull
    private Integer mapId;

    @NotNull
    private Region region;

    @NotNull
    private OffsetDateTime updated = OffsetDateTime.now();

    private Integer duration;

    public Match(){}

    public Match(Long id, @NotNull OffsetDateTime date, @NotNull MatchType type, @NotNull Integer mapId, @NotNull Region region)
    {
        super(date, type);
        this.id = id;
        this.mapId = mapId;
        this.region = region;
    }

    public Match
    (
        Long id,
        @NotNull OffsetDateTime date,
        @NotNull MatchType type,
        @NotNull Integer mapId,
        @NotNull Region region,
        Integer duration
    )
    {
        super(date, type);
        this.id = id;
        this.mapId = mapId;
        this.region = region;
        this.duration = duration;
    }

    public static Match of(BlizzardMatch match, Integer mapId, Region region)
    {
        return new Match(null, match.getDate(), match.getType(), mapId, region);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Match otherMatch = (Match) o;
        return getDate().isEqual(otherMatch.getDate())
            && getType() == otherMatch.getType()
            && getMapId().equals(otherMatch.getMapId())
            && getRegion() == otherMatch.getRegion();
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getDate().toEpochSecond(), getType(), getMapId(), getRegion());
    }

    @Override
    public String toString()
    {
        return String.format("%s[%s %s %s %s]", Match.class.getSimpleName(), getDate(), getType(), getMapId(), getRegion());
    }

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Integer getMapId()
    {
        return mapId;
    }

    public void setMapId(Integer mapId)
    {
        this.mapId = mapId;
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

    public Integer getDuration()
    {
        return duration;
    }

    public void setDuration(Integer duration)
    {
        this.duration = duration;
    }

}
