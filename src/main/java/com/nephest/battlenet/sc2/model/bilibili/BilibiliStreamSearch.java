// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.bilibili;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BilibiliStreamSearch
{

    private BilibiliStreamSearchData data;

    public BilibiliStreamSearch()
    {
    }

    public BilibiliStreamSearch(BilibiliStreamSearchData data)
    {
        this.data = data;
    }

    public BilibiliStreamSearchData getData()
    {
        return data;
    }

    public void setData(BilibiliStreamSearchData data)
    {
        this.data = data;
    }

}
