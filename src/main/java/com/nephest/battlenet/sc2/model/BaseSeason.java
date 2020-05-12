// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import javax.validation.constraints.NotNull;

public class BaseSeason
{

    @NotNull
    private Integer number;

    @NotNull
    private Integer year;

    public BaseSeason(){}

    public BaseSeason
    (
        Integer year,
        Integer number
    )
    {
        this.number = number;
        this.year = year;
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

}

