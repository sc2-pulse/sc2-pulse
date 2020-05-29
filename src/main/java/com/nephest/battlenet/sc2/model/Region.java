// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

public enum Region
implements Identifiable
{

    US(1, "https://us.api.blizzard.com/"),
    EU(2, "https://eu.api.blizzard.com/"),
    KR(3, "https://kr.api.blizzard.com/"),
    CN(5, "https://gateway.battlenet.com.cn/");

    private final int id;
    private final String baseUrl;

    Region(int id, String baseUrl)
    {
        this.id = id;
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

    public String getBaseUrl()
    {
        return baseUrl;
    }

}
