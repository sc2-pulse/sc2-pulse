// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.local.Season;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LadderUpdateContext
{

    public static final Set<BaseLeague.LeagueType> ALL_LEAGUES =
        Collections.unmodifiableSet(EnumSet.allOf(BaseLeague.LeagueType.class));
    public static final Map<QueueType, Set<BaseLeague.LeagueType>> ALL =
        QueueType.getTypes(StatsService.VERSION).stream()
            .collect(Collectors.toUnmodifiableMap(Function.identity(), t->ALL_LEAGUES));
    public static final Map<QueueType, Set<BaseLeague.LeagueType>> _1V1 = Map.of
    (
        QueueType.LOTV_1V1,
        ALL_LEAGUES
    );

    private final Season season;
    private final Map<QueueType, Set<BaseLeague.LeagueType>> data;

    public LadderUpdateContext(Season season, Map<QueueType, Set<BaseLeague.LeagueType>> data)
    {
        this.season = season;
        this.data = data;
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s]",
            LadderUpdateContext.class.getSimpleName(),
            getSeason(), getData()
        );
    }

    public Season getSeason()
    {
        return season;
    }

    public Map<QueueType, Set<BaseLeague.LeagueType>> getData()
    {
        return data;
    }

}
