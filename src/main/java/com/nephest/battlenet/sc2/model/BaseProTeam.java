// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import jakarta.validation.constraints.NotNull;

public class BaseProTeam
{

    @NotNull
    private String name;

    private String shortName;

    public BaseProTeam(){}

    public BaseProTeam(@NotNull String name, String shortName)
    {
        this.name = name;
        this.shortName = shortName;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getShortName()
    {
        return shortName;
    }

    public void setShortName(String shortName)
    {
        this.shortName = shortName;
    }

}
