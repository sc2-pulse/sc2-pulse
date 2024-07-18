// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

public class LadderUpdate
implements java.io.Serializable
{

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    private Region region;

    @NotNull
    private QueueType queueType;

    @NotNull
    private BaseLeague.LeagueType leagueType;

    @NotNull
    private OffsetDateTime created;

    @NotNull
    private Duration duration;

    public LadderUpdate()
    {
    }

    public LadderUpdate
    (
        @NotNull Region region,
        @NotNull QueueType queueType,
        @NotNull BaseLeague.LeagueType leagueType,
        @NotNull OffsetDateTime created,
        @NotNull Duration duration
    )
    {
        this.region = region;
        this.queueType = queueType;
        this.leagueType = leagueType;
        this.created = created;
        this.duration = duration;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof LadderUpdate that)) {return false;}
        return getRegion() == that.getRegion()
            && getQueueType() == that.getQueueType()
            && getLeagueType() == that.getLeagueType()
            && getCreated().isEqual(that.getCreated());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getRegion(), getQueueType(), getLeagueType(), getCreated());
    }

    @Override
    public String toString()
    {
        return "LadderUpdate{"
            + "region=" + region
            + ", queueType=" + queueType
            + ", leagueType=" + leagueType
            + ", created=" + created
            + '}';
    }

    public @NotNull Region getRegion()
    {
        return region;
    }

    public void setRegion(@NotNull Region region)
    {
        this.region = region;
    }

    public @NotNull QueueType getQueueType()
    {
        return queueType;
    }

    public void setQueueType(@NotNull QueueType queueType)
    {
        this.queueType = queueType;
    }

    public @NotNull BaseLeague.LeagueType getLeagueType()
    {
        return leagueType;
    }

    public void setLeagueType(@NotNull BaseLeague.LeagueType leagueType)
    {
        this.leagueType = leagueType;
    }

    public @NotNull OffsetDateTime getCreated()
    {
        return created;
    }

    public void setCreated(@NotNull OffsetDateTime created)
    {
        this.created = created;
    }

    public @NotNull Duration getDuration()
    {
        return duration;
    }

    public void setDuration(@NotNull Duration duration)
    {
        this.duration = duration;
    }
}
