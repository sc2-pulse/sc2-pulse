// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.League;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.Set;
import java.util.stream.Collectors;

public final class LadderUtil
{

    private LadderUtil(){}

    public static MapSqlParameterSource createSearchParams
    (
        ConversionService conversionService,
        int season,
        Set<Region> regions,
        Set<League.LeagueType> leagueTypes,
        QueueType queueType,
        TeamType teamType
    )
    {
        Set<Integer> regionsInt = regions.stream()
            .map(r->conversionService.convert(r, Integer.class))
            .collect(Collectors.toSet());
        Set<Integer> leaguesInt = leagueTypes.stream()
            .map(l->conversionService.convert(l, Integer.class))
            .collect(Collectors.toSet());
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("seasonId", season)
            .addValue("queueType", conversionService.convert(queueType, Integer.class))
            .addValue("teamType", conversionService.convert(teamType, Integer.class));
        params.addValue("regions", regionsInt);
        params.addValue("leagueTypes", leaguesInt);

        return params;
    }

}
