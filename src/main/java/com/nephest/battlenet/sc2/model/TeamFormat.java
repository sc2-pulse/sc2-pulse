// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import java.util.Optional;

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
        return optionalFrom(name).orElseThrow();
    }

    public static Optional<TeamFormat> optionalFrom(String name)
    {
        String lowerName = name.toLowerCase();
        for(TeamFormat format : TeamFormat.values())
            if(format.getName().equalsIgnoreCase(lowerName)) return Optional.of(format);
        return Optional.empty();
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
