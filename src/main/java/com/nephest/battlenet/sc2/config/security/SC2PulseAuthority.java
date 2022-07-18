// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.model.Identifiable;
import org.springframework.security.core.GrantedAuthority;

public enum SC2PulseAuthority
implements Identifiable, GrantedAuthority
{

    ADMIN("ROLE_ADMIN", "ADMIN", 127),
    ACTUATOR("ROLE_ACTUATOR", "ACTUATOR", 20),
    SERVER_WATCHER("ROLE_SERVER_WATCHER", "SERVER_WATCHER", 11),
    MODERATOR("ROLE_MODERATOR", "MODERATOR", 10),
    SUPPORTER("ROLE_SUPPORTER", "SUPPORTER", 3),
    USER("ROLE_USER", "USER", 1),
    NONE("ROLE_NONE", "NONE", 0);

    private final String roleName;
    private final String name;
    private final int id;

    SC2PulseAuthority(String roleName, String name, int id)
    {
        this.roleName = roleName;
        this.name = name;
        this.id = id;
    }

    public static SC2PulseAuthority from(int id)
    {
        for(SC2PulseAuthority authority : SC2PulseAuthority.values())
            if(authority.id == id) return authority;
        throw new IllegalArgumentException("Invalid id " + id);
    }

    @Override
    public int getId()
    {
        return id;
    }

    @Override
    public String getAuthority()
    {
        return getRoleName();
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
