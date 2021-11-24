// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

public enum Region
implements Identifiable
{

    US(1, "NA", "https://us.api.blizzard.com/"),
    EU(2, "EU", "https://eu.api.blizzard.com/"),
    KR(3, "KR", "https://kr.api.blizzard.com/"),
    CN(5, "CN", "https://gateway.battlenet.com.cn/");

    private final int id;
    private final String name;
    private final String baseUrl;

    Region(int id, String name, String baseUrl)
    {
        this.id = id;
        this.name = name;
        this.baseUrl = baseUrl;
    }

    public static Region from(int id)
    {
        for (Region region : Region.values())
        {
            if (region.getId() == id) return region;
        }

        throw new IllegalArgumentException("Invalid id");
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

}
