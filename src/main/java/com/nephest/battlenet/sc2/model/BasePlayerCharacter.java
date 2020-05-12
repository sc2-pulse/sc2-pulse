// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import javax.validation.constraints.NotNull;

public class BasePlayerCharacter
{

    @NotNull
    private Integer realm;

    @NotNull
    private String name;

    public BasePlayerCharacter(){}

    public BasePlayerCharacter(Integer realm, String name)
    {
        this.realm = realm;
        this.name = name;
    }

    public void setRealm(Integer realm)
    {
        this.realm = realm;
    }

    public Integer getRealm()
    {
        return realm;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return name;
    }

}
