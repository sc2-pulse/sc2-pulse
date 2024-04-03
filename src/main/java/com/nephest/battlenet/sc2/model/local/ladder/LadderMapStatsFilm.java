// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.LeagueTier;
import com.nephest.battlenet.sc2.model.local.MapStatsFilm;
import com.nephest.battlenet.sc2.model.local.MapStatsFilmSpec;
import com.nephest.battlenet.sc2.model.local.MapStatsFrame;
import com.nephest.battlenet.sc2.model.local.SC2Map;
import com.nephest.battlenet.sc2.model.local.Season;
import java.util.List;
import java.util.Map;

public class LadderMapStatsFilm
{

    private final Map<Integer, SC2Map> maps;
    private final Map<Integer, Season> seasons;
    private final Map<Integer, League> leagues;
    private final Map<Integer, LeagueTier> tiers;
    private final Map<Integer, MapStatsFilmSpec> specs;
    private final Map<Integer, MapStatsFilm> films;
    private final List<MapStatsFrame> frames;

    public LadderMapStatsFilm
    (
        Map<Integer, SC2Map> maps,
        Map<Integer, Season> seasons,
        Map<Integer, League> leagues,
        Map<Integer, LeagueTier> tiers,
        Map<Integer, MapStatsFilmSpec> specs,
        Map<Integer, MapStatsFilm> films,
        List<MapStatsFrame> frames
    )
    {
        this.maps = maps;
        this.seasons = seasons;
        this.leagues = leagues;
        this.tiers = tiers;
        this.specs = specs;
        this.films = films;
        this.frames = frames;
    }

    public Map<Integer, SC2Map> getMaps()
    {
        return maps;
    }

    public Map<Integer, Season> getSeasons()
    {
        return seasons;
    }

    public Map<Integer, League> getLeagues()
    {
        return leagues;
    }

    public Map<Integer, LeagueTier> getTiers()
    {
        return tiers;
    }

    public Map<Integer, MapStatsFilmSpec> getSpecs()
    {
        return specs;
    }

    public Map<Integer, MapStatsFilm> getFilms()
    {
        return films;
    }

    public List<MapStatsFrame> getFrames()
    {
        return frames;
    }

}
