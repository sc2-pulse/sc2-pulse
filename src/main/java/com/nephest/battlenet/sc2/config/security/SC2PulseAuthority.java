// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

public enum SC2PulseAuthority
{

    ADMIN("ROLE_ADMIN", "ADMIN");

    private final String roleName;
    private final String name;

    SC2PulseAuthority(String roleName, String name)
    {
        this.roleName = roleName;
        this.name = name;
    }

    public String getName()
    {
        return name;
    }

    public String getRoleName()
    {
        return roleName;
    }

}
