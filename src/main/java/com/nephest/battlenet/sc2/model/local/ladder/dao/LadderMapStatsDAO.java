// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.MapStats;
import com.nephest.battlenet.sc2.model.local.SC2Map;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.dao.LeagueDAO;
import com.nephest.battlenet.sc2.model.local.dao.MapStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.SC2MapDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMapStats;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Repository
public class LadderMapStatsDAO
{

    private static final String FIND_SEASON_LEAGUES_QUERY =
        "SELECT "
        + SeasonDAO.STD_SELECT + ", "
        + LeagueDAO.STD_SELECT
        + "FROM season "
        + "INNER JOIN league ON season.id = league.season_id "
        + "WHERE season.battlenet_id = :seasonId "
        + "AND season.region IN (:regions) "
        + "AND league.queue_type = :queueType "
        + "AND league.team_type = :teamType "
        + "AND league.type IN (:leagueTypes)";

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;
    private final MapStatsDAO mapStatsDAO;
    private final SC2MapDAO mapDAO;

    @Autowired
    public LadderMapStatsDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService,
        SC2MapDAO mapDAO,
        MapStatsDAO mapStatsDAO
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        this.mapDAO = mapDAO;
        this.mapStatsDAO = mapStatsDAO;
    }

    public LadderMapStats find
    (
        int season,
        Collection<Region> regions,
        Collection<League.LeagueType> leagueTypes,
        QueueType queueType,
        TeamType teamType,
        Integer mapId
    )
    {
        MapSqlParameterSource params = LadderUtil
            .createSearchParams(conversionService, season, regions, leagueTypes, queueType, teamType);
        List<Tuple2<Season, League>> seasonLeagues = template.query(FIND_SEASON_LEAGUES_QUERY, params,
            (rs, i)->Tuples.of
            (
                SeasonDAO.getStdRowMapper().mapRow(rs, i),
                LeagueDAO.getStdRowMapper().mapRow(rs, i)
            ));
        if(seasonLeagues.isEmpty()) return LadderMapStats.empty();

        Map<Integer, Season> seasons = seasonLeagues.stream()
            .map(Tuple2::getT1)
            .distinct()
            .collect(Collectors.toMap(Season::getId, Function.identity()));
        Map<Integer, League> leagues = seasonLeagues.stream()
            .map(Tuple2::getT2)
            .collect(Collectors.toMap(League::getId, Function.identity()));
        List<MapStats> stats = mapStatsDAO.find(mapId, new ArrayList<>(leagues.keySet()));
        SC2Map map = mapId == null ? null : mapDAO.find(List.of(mapId)).get(0);
        List<SC2Map> maps = mapDAO.find(season, regions, leagueTypes, queueType, teamType);
        return new LadderMapStats(stats, seasons, leagues, map, maps);
    }

}
