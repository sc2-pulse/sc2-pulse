// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nephest.battlenet.sc2.config.convert.jackson.EpochSecondToOffsetDateTimeConverter;
import java.time.OffsetDateTime;

public class BlizzardDataSeason
extends BlizzardSeason
{

    BlizzardDataSeason(){}

    public BlizzardDataSeason
    (
        Integer id,
        Integer year,
        Integer number,
        OffsetDateTime start,
        OffsetDateTime end
    )
    {
        super(id, year, number, start, end);
    }

    @JsonProperty("id")
    public void setId(Integer id)
    {
        super.setId(id);
    }

    @JsonProperty("start_timestamp")
    @JsonDeserialize(converter = EpochSecondToOffsetDateTimeConverter.class)
    public void setStart(OffsetDateTime start)
    {
        super.setStart(start);
    }

    @JsonProperty("end_timestamp")
    @JsonDeserialize(converter = EpochSecondToOffsetDateTimeConverter.class)
    public void setEnd(OffsetDateTime end)
    {
        super.setEnd(end);
    }

}
