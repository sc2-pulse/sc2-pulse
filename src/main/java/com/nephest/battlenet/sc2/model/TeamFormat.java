// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

public enum TeamFormat
{

    _1V1("1v1", 1),
    _2V2("2v2", 2),
    _3V3("3v3", 3),
    _4V4("4v4", 4),
    ARCHON("Archon", 2);

    private final String name;
    private final int memberCount;

    TeamFormat(String name, int memberCount)
    {
        this.name = name;
        this.memberCount = memberCount;
    }

    public static TeamFormat from(String name)
    {
        for(TeamFormat format : TeamFormat.values())
            if(format.getName().equalsIgnoreCase(name)) return format;

        throw new IllegalArgumentException("Invalid name");
    }

    public int getMemberCount(TeamType teamType)
    {
        if(teamType == TeamType.RANDOM) return 1;
        return getMemberCount();
    }

    public String getName()
    {
        return name;
    }

    public int getMemberCount()
    {
        return memberCount;
    }

}
