// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nephest.battlenet.sc2.config.convert.jackson.EpochSecondToLocalDateConverter;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;

public class BaseSeason
{

    @NotNull
    private Integer number;

    @NotNull
    private Integer year;

    /*
        The upstream defines these dates as timestamp, but we are using the date because we don`t really need
        the time portion. Historical season data (on liquipedia) does not have a time portion either. Furthermore,
        there are multiple upstream seasons(for different regions) and only one historical date for all of them,
        which makes timestamp type completely useless and misleading.
     */
    @NotNull
    protected LocalDate start;

    @NotNull
    protected LocalDate end;

    public BaseSeason(){}

    public BaseSeason(@NotNull Integer year, @NotNull Integer number, @NotNull LocalDate start, @NotNull LocalDate end)
    {
        this.number = number;
        this.year = year;
        this.start = start;
        this.end = end;
    }

    public void setNumber(Integer number)
    {
        this.number = number;
    }

    public Integer getNumber()
    {
        return number;
    }

    public void setYear(Integer year)
    {
        this.year = year;
    }

    public Integer getYear()
    {
        return year;
    }

    /*
        Using different names for getters and setters because we want to sync the property names
        between our endpoints while also being able to correctly parse the upstream property names
     */
    @JsonProperty("start")
    public LocalDate getStart()
    {
        return start;
    }

    @JsonProperty("startDate")
    @JsonDeserialize(converter = EpochSecondToLocalDateConverter.class)
    public void setStart(LocalDate start)
    {
        this.start = start;
    }

    @JsonProperty("end")
    public LocalDate getEnd()
    {
        return end;
    }

    @JsonProperty("endDate")
    @JsonDeserialize(converter = EpochSecondToLocalDateConverter.class)
    public void setEnd(LocalDate end)
    {
        this.end = end;
    }
    
}

