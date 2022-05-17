// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.MapStats;
import com.nephest.battlenet.sc2.model.local.SC2Map;
import com.nephest.battlenet.sc2.model.local.Season;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;

public class LadderMapStats
{

    @NotNull
    private final List<MapStats> stats;

    @NotNull
    private final Map<Integer, Season> seasons;

    @NotNull
    private final Map<Integer, League> leagues;

    private final SC2Map map;

    @NotNull
    private final List<SC2Map> maps;

    public LadderMapStats(List<MapStats> stats, Map<Integer, Season> seasons, Map<Integer, League> leagues, SC2Map map, List<SC2Map> maps)
    {
        this.stats = stats;
        this.seasons = seasons;
        this.leagues = leagues;
        this.map = map;
        this.maps = maps;
    }

    public static LadderMapStats empty()
    {
        return new LadderMapStats(List.of(), Map.of(), Map.of(), null, List.of());
    }

    public List<MapStats> getStats()
    {
        return stats;
    }

    public Map<Integer, Season> getSeasons()
    {
        return seasons;
    }

    public Map<Integer, League> getLeagues()
    {
        return leagues;
    }

    public SC2Map getMap()
    {
        return map;
    }

    public List<SC2Map> getMaps()
    {
        return maps;
    }

}
