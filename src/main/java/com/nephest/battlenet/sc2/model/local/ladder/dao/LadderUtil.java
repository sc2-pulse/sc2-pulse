// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.League;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Repository
public class LadderUtil
{

    private static final Map<String, Object> DEFAULT_TEAM_MEMBER_QUERY_VALUES;

    static
    {
        Map<String, Object> vals = new HashMap<>();
        vals.put("region0", null);
        vals.put("region1", null);
        vals.put("region2", null);
        vals.put("region3", null);
        vals.put("leagueType0", null);
        vals.put("leagueType1", null);
        vals.put("leagueType2", null);
        vals.put("leagueType3", null);
        vals.put("leagueType4", null);
        vals.put("leagueType5", null);
        vals.put("leagueType6", null);
        DEFAULT_TEAM_MEMBER_QUERY_VALUES = Collections.unmodifiableMap(vals);
    }

    private final ConversionService conversionService;

    @Autowired
    public LadderUtil
    (
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.conversionService = conversionService;
    }

    public MapSqlParameterSource createSearchParams
    (
        long season,
        Set<Region> regions,
        Set<League.LeagueType> leagueTypes,
        QueueType queueType,
        TeamType teamType
    )
    {
        MapSqlParameterSource params = new MapSqlParameterSource(DEFAULT_TEAM_MEMBER_QUERY_VALUES)
            .addValue("seasonId", season)
            .addValue("queueType", conversionService.convert(queueType, Integer.class))
            .addValue("teamType", conversionService.convert(teamType, Integer.class));

        int i = 0;
        for(Region region : regions)
        {
            params.addValue("region" + i, conversionService.convert(region, Integer.class));
            i++;
        }

        i = 0;
        for(League.LeagueType leagueType : leagueTypes)
        {
            params.addValue("leagueType" + i, conversionService.convert(leagueType, Integer.class));
            i++;
        }

        return params;
    }

}
