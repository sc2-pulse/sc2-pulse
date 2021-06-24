// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import javax.validation.constraints.NotNull;

public class BasePlayerCharacter
{

    public static final String DEFAULT_HASH_SUFFIX = "#1";

    @NotNull
    private Integer realm;

    @NotNull
    private String name;

    public BasePlayerCharacter(){}

    public BasePlayerCharacter(Integer realm, String name)
    {
        this.realm = realm;
        setName(name);
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
        //Some characters can have an empty name. Use a fake name for them.
        if(name == null) {
            this.name = "f" + DEFAULT_HASH_SUFFIX;
        } else
        {
            name = name.contains("#") ? name : name + DEFAULT_HASH_SUFFIX;
            this.name = name;
        }
    }

    public String getName()
    {
        return name;
    }

}
