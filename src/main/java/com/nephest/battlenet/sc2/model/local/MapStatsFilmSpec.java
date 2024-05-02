// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.Race;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Objects;

public class MapStatsFilmSpec
implements Serializable
{

    private static final long serialVersionUID = 1L;

    public static TemporalUnit FRAME_DURATION_UNIT = ChronoUnit.SECONDS;

    private Integer id;

    @NotNull
    private Race race;

    @NotNull
    private Race versusRace;

    @NotNull
    private Duration frameDuration;

    public MapStatsFilmSpec()
    {
    }

    public MapStatsFilmSpec(Integer id, Race race, Race versusRace, Duration frameDuration)
    {
        this.id = id;
        this.race = race;
        this.versusRace = versusRace;
        this.frameDuration = frameDuration;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof MapStatsFilmSpec that)) {return false;}
        return getRace() == that.getRace()
            && getVersusRace() == that.getVersusRace()
            && Objects.equals(getFrameDuration(), that.getFrameDuration());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getRace(), getVersusRace(), getFrameDuration());
    }

    @Override
    public String toString()
    {
        return "MapStatsFilmSpec{"
            + "race=" + race
            + ", versusRace=" + versusRace
            + ", frameDuration=" + frameDuration
            + '}';
    }

    public Integer getId()
    {
        return id;
    }

    public void setId(Integer id)
    {
        this.id = id;
    }

    public Race getRace()
    {
        return race;
    }

    public void setRace(Race race)
    {
        this.race = race;
    }

    public Race getVersusRace()
    {
        return versusRace;
    }

    public void setVersusRace(Race versusRace)
    {
        this.versusRace = versusRace;
    }

    public Duration getFrameDuration()
    {
        return frameDuration;
    }

    public void setFrameDuration(Duration frameDuration)
    {
        this.frameDuration = frameDuration;
    }

}
