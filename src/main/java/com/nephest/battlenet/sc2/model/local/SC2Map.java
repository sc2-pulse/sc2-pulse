// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import javax.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.Objects;

public class SC2Map
implements java.io.Serializable
{

    private static final long serialVersionUID = 1L;

    public static final Comparator<SC2Map> NATURAL_ID_COMPARATOR = Comparator.comparing(SC2Map::getName);

    @NotNull
    private Integer id;

    @NotNull
    private String name;

    public SC2Map(){}

    public SC2Map(Integer id, String name)
    {
        this.id = id;
        this.name = name;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof SC2Map)) return false;
        SC2Map sc2Map = (SC2Map) o;
        return getName().equals(sc2Map.getName());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getName());
    }

    @Override
    public String toString()
    {
        return String.format("%s[%s]", SC2Map.class.getSimpleName(), getName());
    }

    public Integer getId()
    {
        return id;
    }

    public void setId(Integer id)
    {
        this.id = id;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

}
