// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import jakarta.validation.constraints.NotNull;

public class BaseClan
{

    @NotNull
    private String tag;

    public BaseClan(){}

    public BaseClan(String tag)
    {
        this.tag = tag;
    }

    public String getTag()
    {
        return tag;
    }

    public void setTag(String tag)
    {
        this.tag = tag;
    }

}
