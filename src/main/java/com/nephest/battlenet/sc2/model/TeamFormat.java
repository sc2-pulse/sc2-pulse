// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

public enum TeamFormat
{

    _1V1(1), _2V2(2), _3V3(3), _4V4(4), ARCHON(2);

    private final int memberCount;

    TeamFormat(int memberCount)
    {
        this.memberCount = memberCount;
    }

    public int getMemberCount(TeamType teamType)
    {
        if(teamType == TeamType.RANDOM) return 1;
        return getMemberCount();
    }

    public int getMemberCount()
    {
        return memberCount;
    }

}
