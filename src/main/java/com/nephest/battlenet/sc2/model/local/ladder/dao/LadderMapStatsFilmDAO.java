// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.LeagueTier;
import com.nephest.battlenet.sc2.model.local.MapStatsFilm;
import com.nephest.battlenet.sc2.model.local.MapStatsFilmSpec;
import com.nephest.battlenet.sc2.model.local.MapStatsFrame;
import com.nephest.battlenet.sc2.model.local.MatchUp;
import com.nephest.battlenet.sc2.model.local.SC2Map;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.dao.LeagueDAO;
import com.nephest.battlenet.sc2.model.local.dao.LeagueTierDAO;
import com.nephest.battlenet.sc2.model.local.dao.MapStatsFilmDAO;
import com.nephest.battlenet.sc2.model.local.dao.MapStatsFilmFrameDAO;
import com.nephest.battlenet.sc2.model.local.dao.MapStatsFilmSpecDAO;
import com.nephest.battlenet.sc2.model.local.dao.SC2MapDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMapStatsFilm;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class LadderMapStatsFilmDAO
{

    private final MapStatsFilmSpecDAO mapStatsFilmSpecDAO;
    private final MapStatsFilmDAO mapStatsFilmDAO;
    private final MapStatsFilmFrameDAO mapStatsFilmFrameDAO;
    private final SeasonDAO seasonDAO;
    private final LeagueDAO leagueDAO;
    private final LeagueTierDAO leagueTierDAO;
    private final SC2MapDAO mapDAO;

    @Autowired
    public LadderMapStatsFilmDAO
    (
        MapStatsFilmSpecDAO mapStatsFilmSpecDAO,
        MapStatsFilmDAO mapStatsFilmDAO,
        MapStatsFilmFrameDAO mapStatsFilmFrameDAO,
        SeasonDAO seasonDAO,
        LeagueDAO leagueDAO,
        LeagueTierDAO leagueTierDAO,
        SC2MapDAO mapDAO
    )
    {
        this.mapStatsFilmSpecDAO = mapStatsFilmSpecDAO;
        this.mapStatsFilmDAO = mapStatsFilmDAO;
        this.mapStatsFilmFrameDAO = mapStatsFilmFrameDAO;
        this.seasonDAO = seasonDAO;
        this.leagueDAO = leagueDAO;
        this.leagueTierDAO = leagueTierDAO;
        this.mapDAO = mapDAO;
    }

    public LadderMapStatsFilm find
    (
        Set<MatchUp> matchUps,
        Duration frameDuration,
        Integer frameNumberMax,
        int season,
        Set<Region> regions,
        QueueType queue,
        TeamType teamType,
        BaseLeague.LeagueType league,
        BaseLeagueTier.LeagueTierType tier,
        Set<Boolean> crossTier
    )
    {
        Objects.requireNonNull(frameDuration);
        if(matchUps.isEmpty() || regions.isEmpty()) return null;
        Map<Integer, MapStatsFilmSpec> specs = mapStatsFilmSpecDAO.find(matchUps, frameDuration)
            .stream()
            .collect(Collectors.toMap(MapStatsFilmSpec::getId, Function.identity()));
        if(specs.isEmpty()) return null;

        Map<Integer, Season> seasons = seasonDAO.findListByBattlenetId(season).stream()
            .filter(s->regions.contains(s.getRegion()))
            .collect(Collectors.toMap(Season::getId, Function.identity()));
        if(seasons.isEmpty()) return null;

        Map<Integer, League> leagues = leagueDAO
            .find
            (
                seasons.keySet(),
                queue != null ? EnumSet.of(queue) : Set.of(),
                teamType,
                league != null ? EnumSet.of(league) : Set.of()
            )
            .stream()
            .collect(Collectors.toMap(League::getId, Function.identity()));
        Map<Integer, LeagueTier> tiers = leagueTierDAO
            .find(leagues.keySet(), tier != null ? EnumSet.of(tier) : Set.of())
            .stream()
            .collect(Collectors.toMap(LeagueTier::getId, Function.identity()));
        Map<Integer, MapStatsFilm> films = mapStatsFilmDAO
            .find(specs.keySet(), tiers.keySet(), Set.of(), crossTier)
            .stream()
            .collect(Collectors.toMap(MapStatsFilm::getId, Function.identity()));
        if(films.isEmpty()) return null;

        List<MapStatsFrame> frames = mapStatsFilmFrameDAO.find(films.keySet(), frameNumberMax);
        List<Integer> mapIds = films.values().stream()
            .map(MapStatsFilm::getMapId)
            .distinct()
            .collect(Collectors.toList());
        Map<Integer, SC2Map> maps = mapDAO.find(mapIds).stream()
            .collect(Collectors.toMap(SC2Map::getId, Function.identity()));

        return new LadderMapStatsFilm(maps, seasons, leagues, tiers, specs, films, frames);
    }


}
