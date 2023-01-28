// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.connection;

public enum PulseConnectionParameter
{

    REGION("region"),
    RACE("race"),
    LEAGUE("league"),
    RATING("rating");

    private final String name;

    PulseConnectionParameter(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return name;
    }

}
