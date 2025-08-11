// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.League;
import java.util.Collection;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

public final class LadderUtil
{

    private LadderUtil(){}

    public static MapSqlParameterSource createSearchParams
    (
        ConversionService conversionService,
        int season,
        Collection<Region> regions,
        Collection<League.LeagueType> leagueTypes,
        QueueType queueType,
        TeamType teamType
    )
    {
        return new MapSqlParameterSource()
            .addValue("seasonId", season)
            .addValue("queueType", conversionService.convert(queueType, Integer.class))
            .addValue("teamType", conversionService.convert(teamType, Integer.class))
            .addValue
            (
                "regions",
                regions.isEmpty()
                    ? null
                    : regions.stream()
                        .distinct()
                        .map(r->conversionService.convert(r, Integer.class))
                        .toArray(Integer[]::new)
            )
            .addValue
            (
                "leagueTypes",
                leagueTypes.isEmpty()
                    ? null
                    : leagueTypes.stream()
                        .distinct()
                        .map(l->conversionService.convert(l, Integer.class))
                        .toArray(Integer[]::new)
            );
    }

}
