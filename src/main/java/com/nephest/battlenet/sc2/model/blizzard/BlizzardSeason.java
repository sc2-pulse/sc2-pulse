// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nephest.battlenet.sc2.config.convert.jackson.EpochSecondToLocalDateConverter;
import com.nephest.battlenet.sc2.model.BaseSeason;
import java.time.LocalDate;
import javax.validation.constraints.NotNull;

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
        LocalDate start,
        LocalDate end
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
    @JsonDeserialize(converter = EpochSecondToLocalDateConverter.class)
    public void setStart(LocalDate start)
    {
        super.setStart(start);
    }

    @Override
    @JsonProperty("endDate")
    @JsonDeserialize(converter = EpochSecondToLocalDateConverter.class)
    public void setEnd(LocalDate end)
    {
        super.setEnd(end);
    }

}
