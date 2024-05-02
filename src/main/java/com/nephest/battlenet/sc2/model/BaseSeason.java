// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import jakarta.validation.constraints.NotNull;
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

    public LocalDate getStart()
    {
        return start;
    }

    public void setStart(LocalDate start)
    {
        this.start = start;
    }

    public LocalDate getEnd()
    {
        return end;
    }

    public void setEnd(LocalDate end)
    {
        this.end = end;
    }
    
}

