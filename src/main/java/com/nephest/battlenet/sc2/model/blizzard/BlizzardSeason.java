// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nephest.battlenet.sc2.config.convert.jackson.EpochSecondToOffsetDateTimeConverter;
import com.nephest.battlenet.sc2.model.BaseSeason;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public class BlizzardSeason
extends BaseSeason
{

    @NotNull
    private Integer id;

    public BlizzardSeason(){}

    public BlizzardSeason
    (
        Integer id,
        Integer year,
        Integer number,
        OffsetDateTime start,
        OffsetDateTime end
    )
    {
        super(year, number, start, end);
        this.id = id;
    }

    @JsonProperty("seasonId")
    public void setId(Integer id)
    {
        this.id = id;
    }

    public Integer getId()
    {
        return id;
    }

    @Override
    @JsonProperty("startDate")
    @JsonDeserialize(converter = EpochSecondToOffsetDateTimeConverter.class)
    public void setStart(OffsetDateTime start)
    {
        super.setStart(start);
    }

    @Override
    @JsonProperty("endDate")
    @JsonDeserialize(converter = EpochSecondToOffsetDateTimeConverter.class)
    public void setEnd(OffsetDateTime end)
    {
        super.setEnd(end);
    }

}
