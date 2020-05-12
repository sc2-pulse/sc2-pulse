// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import javax.validation.constraints.NotNull;

public class LadderSeason
{

    @NotNull
    private final Long id;

    @NotNull
    private final Integer year;

    @NotNull
    private final Integer number;

    public LadderSeason(Long id, Integer year, Integer number)
    {
        this.id = id;
        this.year = year;
        this.number = number;
    }

    public Long getId()
    {
        return id;
    }

    public Integer getYear()
    {
        return year;
    }

    public Integer getNumber()
    {
        return number;
    }

}
