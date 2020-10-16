// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import java.net.URL;

public enum Partition
implements Identifiable
{

    GLOBAL(0), CHINA(1);

    private final int id;

    Partition(int id)
    {
        this.id = id;
    }

    public static Partition of(Region region)
    {
        if(region == Region.CN) return CHINA;
        return GLOBAL;
    }

    public static Partition ofIssuer(String issuer)
    {
        String[] split = issuer.split("\\.");
        if(split[split.length - 1].startsWith("cn")) return Partition.CHINA;
        return Partition.GLOBAL;
    }

    public static Partition ofIssuer(URL issuer)
    {
        return ofIssuer(issuer.getHost());
    }


    @Override
    public int getId()
    {
        return id;
    }

}
