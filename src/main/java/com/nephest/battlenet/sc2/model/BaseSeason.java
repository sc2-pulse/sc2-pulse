// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public class BaseSeason
{

    @NotNull
    private Integer number;

    @NotNull
    private Integer year;

    @NotNull
    protected OffsetDateTime start;

    @NotNull
    protected OffsetDateTime end;

    public BaseSeason(){}

    public BaseSeason
    (
        @NotNull Integer year,
        @NotNull Integer number,
        @NotNull OffsetDateTime start,
        @NotNull OffsetDateTime end
    )
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

    public OffsetDateTime getStart()
    {
        return start;
    }

    public void setStart(OffsetDateTime start)
    {
        this.start = start;
    }

    public OffsetDateTime getEnd()
    {
        return end;
    }

    public void setEnd(OffsetDateTime end)
    {
        this.end = end;
    }
    
}

