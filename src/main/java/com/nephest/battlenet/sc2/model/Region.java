// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Region
implements Identifiable
{

    US(1, "NA", "https://us.api.blizzard.com/", "https://starcraft2.com/en-us/api/"),
    EU(2, "EU", "https://eu.api.blizzard.com/", "https://starcraft2.com/en-gb/api/"),
    KR(3, "KR", "https://kr.api.blizzard.com/", "https://starcraft2.com/ko-kr/api/"),
    CN(5, "CN", "https://gateway.battlenet.com.cn/", null);

    private final int id;
    private final String name;
    private final String baseUrl;
    private final String baseWebUrl;

    Region(int id, String name, String baseUrl, String baseWebUrl)
    {
        this.id = id;
        this.name = name;
        this.baseUrl = baseUrl;
        this.baseWebUrl = baseWebUrl;
    }

    public static Region from(String name)
    {
        for(Region region : Region.values())
            if(region.getName().equalsIgnoreCase(name)) return region;

        throw new IllegalArgumentException("Invalid name: " + name);
    }

    public static Region from(int id)
    {
        for (Region region : Region.values())
        {
            if (region.getId() == id) return region;
        }

        throw new IllegalArgumentException("Invalid id");
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Region fromVar(String var)
    {
        return var.length() > 1 ? Region.valueOf(var) : from(Integer.parseInt(var));
    }

    @Override
    public int getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    public String getBaseUrl()
    {
        return baseUrl;
    }

    public String getBaseWebUrl()
    {
        return baseWebUrl;
    }

}
