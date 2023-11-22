// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.nephest.battlenet.sc2.util.MiscUtil;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public enum Region
implements Identifiable, MultiAliasName
{

    US
    (
        1,
        "NA",
        Set.of("US", "United States", "America", "Americas", "North America"),
        "https://us.api.blizzard.com/",
        "https://starcraft2.blizzard.com/en-us/api/",
        "https://cache-us.battle.net"
    ),
    EU
    (
        2,
        "EU",
        Set.of("Europe"),
        "https://eu.api.blizzard.com/",
        "https://starcraft2.blizzard.com/en-gb/api/",
        "https://cache-eu.battle.net"
    ),
    KR
    (
        3,
        "KR",
        Set.of("Korea", "South Korea", "TW", "Taiwan", "Asia"),
        "https://kr.api.blizzard.com/",
        "https://starcraft2.blizzard.com/ko-kr/api/",
        "https://cache-kr.battle.net"
    ),
    CN
    (
        5,
        "CN",
        Set.of("China"),
        "https://gateway.battlenet.com.cn/",
        null,
        null
    );

    public static final Map<Region, Set<String>> ALL_NAMES_MAP =
        MiscUtil.generateAllNamesMap(Region.class);

    private final int id;
    private final String name;
    private final Set<String> additionalNames;
    private final String baseUrl;
    private final String baseWebUrl;
    private final String cacheUrl;

    Region
    (
        int id,
        String name,
        Set<String> additionalNames,
        String baseUrl,
        String baseWebUrl,
        String cacheUrl
    )
    {
        this.id = id;
        this.name = name;
        this.additionalNames = additionalNames;
        this.baseUrl = baseUrl;
        this.baseWebUrl = baseWebUrl;
        this.cacheUrl = cacheUrl;
    }

    public static Region from(String name)
    {
        return optionalFrom(name).orElseThrow();
    }

    public static Optional<Region> optionalFrom(String name)
    {
        String lowerName = name.toLowerCase();
        for(Region region : Region.values())
            if(region.getName().equalsIgnoreCase(lowerName)) return Optional.of(region);
        return Optional.empty();
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
        return var.length() > 1 ? Region.valueOf(var.toUpperCase()) : from(Integer.parseInt(var));
    }

    @Override
    public int getId()
    {
        return id;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public Set<String> getAdditionalNames()
    {
        return additionalNames;
    }

    public String getBaseUrl()
    {
        return baseUrl;
    }

    public String getBaseWebUrl()
    {
        return baseWebUrl;
    }

    public String getCacheUrl()
    {
        return cacheUrl;
    }

}
