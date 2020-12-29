// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nephest.battlenet.sc2.config.convert.jackson.EpochSecondToLocalDateConverter;

import java.time.LocalDate;

public class BlizzardDataSeason
extends BlizzardSeason
{

    BlizzardDataSeason(){}

    public BlizzardDataSeason
    (
        Integer id,
        Integer year,
        Integer number,
        LocalDate start,
        LocalDate end
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
    @JsonDeserialize(converter = EpochSecondToLocalDateConverter.class)
    public void setStart(LocalDate start)
    {
        super.setStart(start);
    }

    @JsonProperty("end_timestamp")
    @JsonDeserialize(converter = EpochSecondToLocalDateConverter.class)
    public void setEnd(LocalDate end)
    {
        super.setEnd(end);
    }

}
