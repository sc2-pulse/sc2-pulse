// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;

public class BasePlayerCharacter
{

    public static final String DEFAULT_FAKE_NAME = "f";
    public static final String DEFAULT_HASH_SUFFIX = "#1";
    public static final String DEFAULT_FAKE_FULL_NAME = DEFAULT_FAKE_NAME + DEFAULT_HASH_SUFFIX;

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

    @JsonAlias({"realmId"})
    public void setRealm(Integer realm)
    {
        this.realm = realm;
    }

    public Integer getRealm()
    {
        return realm;
    }

    public void setName(String name, boolean autoFix)
    {
        if(!autoFix)
        {
            this.name = name;
        }
        //Some characters can have an empty name. Use a fake name for them.
        else if(name == null) {
            this.name = DEFAULT_FAKE_FULL_NAME;
        } else
        {
            name = name.contains("#") ? name : name + DEFAULT_HASH_SUFFIX;
            this.name = name;
        }
    }

    @JsonAlias({"displayName"})
    public void setName(String name)
    {
        setName(name, true);
    }

    public String getName()
    {
        return name;
    }

}
