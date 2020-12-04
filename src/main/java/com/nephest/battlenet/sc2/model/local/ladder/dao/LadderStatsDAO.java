// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.LeagueStats;
import com.nephest.battlenet.sc2.model.local.QueueStats;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.dao.LeagueDAO;
import com.nephest.battlenet.sc2.model.local.dao.LeagueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.QueueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderSearchStatsResult;
import com.nephest.battlenet.sc2.model.local.ladder.MergedLadderSearchStatsResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class LadderStatsDAO
{

    private static final String LADDER_SEARCH_STATS_QUERY =
        "SELECT "
        + "season.id AS \"season.id\","
        + "season.battlenet_id AS \"season.battlenet_id\","
        + "season.region AS \"season.region\","
        + "season.year AS \"season.year\","
        + "season.number AS \"season.number\","
        + "\"start\" AS \"season.start\", \"end\" AS \"season.end\", "
        + "league.id AS \"league.id\","
        + "league.season_id AS \"league.season_id\","
        + "league.type AS \"league.type\","
        + "league.queue_type AS \"league.queue_type\","
        + "league.team_type AS \"league.team_type\","
        + "league_stats.league_id AS \"league_stats.league_id\","
        + "league_stats.team_count, "
        + "league_stats.terran_games_played, "
        + "league_stats.protoss_games_played, "
        + "league_stats.zerg_games_played, "
        + "league_stats.random_games_played "


        + "FROM league_stats "
        + "INNER JOIN league ON league_stats.league_id=league.id "
        + "INNER JOIN season ON league.season_id = season.id "

        + "WHERE "
        + "season.region IN (:region0, :region1, :region2, :region3) "
        + "AND league.type IN (:leagueType0, :leagueType1, :leagueType2, :leagueType3, :leagueType4, :leagueType5, :leagueType6) "
        + "AND league.queue_type=:queueType "
        + "AND league.team_type=:teamType ";

    private static final String FIND_LEAGUE_TIER_BOUNDS_QUERY =
        "SELECT "
        + "season.region, "
        + "league.type AS \"league.type\", "
        + "league_tier.type AS \"league_tier.type\", league_tier.min_rating, league_tier.max_rating "

        + "FROM league_tier "
        + "INNER JOIN league ON league_tier.league_id=league.id "
        + "INNER JOIN season ON league.season_id=season.id "

        + "WHERE "
        + "season.battlenet_id=:seasonId "
        + "AND season.region IN (:region0, :region1, :region2, :region3) "
        + "AND league.type IN (:leagueType0, :leagueType1, :leagueType2, :leagueType3, :leagueType4, :leagueType5, :leagueType6) "
        + "AND league.queue_type=:queueType "
        + "AND league.team_type=:teamType ";

    private final NamedParameterJdbcTemplate template;
    private ConversionService conversionService;
    private final LadderUtil ladderUtil;
    private final QueueStatsDAO queueStatsDAO;

    private final ResultSetExtractor<Map<Integer, Map<Region, Map<BaseLeague.LeagueType, LadderSearchStatsResult>>>>
        LADDER_STATS_EXTRACTOR =
        (rs)->
        {
            Map<Integer, Map<Region, Map<BaseLeague.LeagueType, LadderSearchStatsResult>>> result = new HashMap<>();
            int num = 1;
            while(rs.next())
            {
                Season season = SeasonDAO.getStdRowMapper().mapRow(rs, num);
                League league = LeagueDAO.getStdRowMapper().mapRow(rs, num);
                LeagueStats leagueStats = LeagueStatsDAO.STD_ROW_MAPPER.mapRow(rs, num);
                Map<Region, Map<BaseLeague.LeagueType, LadderSearchStatsResult>> regionResults =
                    result.computeIfAbsent(season.getBattlenetId(), (reg)->new EnumMap<>(Region.class));
                Map<BaseLeague.LeagueType, LadderSearchStatsResult> leagueResults =
                    regionResults.computeIfAbsent(season.getRegion(), (reg)->new EnumMap<>(BaseLeague.LeagueType.class));
                leagueResults.put(league.getType(), new LadderSearchStatsResult(season, league, leagueStats));
                num++;
            }
            return result;
        };

    private final ResultSetExtractor<Map<Region, Map<BaseLeague.LeagueType, Map<BaseLeagueTier.LeagueTierType, Integer[]>>>> LEAGUE_TIER_BOUNDS_EXTRACTOR =
    (rs)->
    {
        Map<Region, Map<BaseLeague.LeagueType, Map<BaseLeagueTier.LeagueTierType, Integer[]>>> result = new EnumMap<>(Region.class);
        while(rs.next())
        {
            Region region = conversionService.convert(rs.getInt("region"), Region.class);
            BaseLeague.LeagueType league = conversionService.convert(rs.getInt("league.type"), BaseLeague.LeagueType.class);
            BaseLeagueTier.LeagueTierType
                tier = conversionService.convert(rs.getInt("league_tier.type"), BaseLeagueTier.LeagueTierType.class);
            Integer minRating = rs.getInt("min_rating");
            Integer maxRating = rs.getInt("max_rating");

            Map<BaseLeague.LeagueType, Map<BaseLeagueTier.LeagueTierType, Integer[]>> leagues =
                result.computeIfAbsent(region, (reg)->new EnumMap<>(BaseLeague.LeagueType.class));
            Map<BaseLeagueTier.LeagueTierType, Integer[]> tiers =
                leagues.computeIfAbsent(league, (lea)->new EnumMap<>(BaseLeagueTier.LeagueTierType.class));
            Integer[] bounds =
                tiers.computeIfAbsent(tier, (tie)->new Integer[2]);
            bounds[0] = minRating;
            bounds[1] = maxRating;
        }
        return result;
    };

    @Autowired
    public LadderStatsDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService,
        @Autowired LadderUtil ladderUtil,
        @Autowired QueueStatsDAO queueStatsDAO
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        this.ladderUtil = ladderUtil;
        this.queueStatsDAO = queueStatsDAO;
    }

    @Cacheable
    (
        cacheNames="search-ladder-league-bounds",
        condition="#a0 eq #root.target.seasonDAO.maxBattlenetId"
    )
    public Map<Region, Map<BaseLeague.LeagueType, Map<BaseLeagueTier.LeagueTierType, Integer[]>>> findLeagueBounds
    (
        int season,
        Set<Region> regions,
        Set<League.LeagueType> leagueTypes,
        QueueType queueType,
        TeamType teamType
    )
    {
        MapSqlParameterSource params =
            ladderUtil.createSearchParams(season, regions, leagueTypes, queueType, teamType);
        return template
            .query(FIND_LEAGUE_TIER_BOUNDS_QUERY, params, LEAGUE_TIER_BOUNDS_EXTRACTOR);
    }

    @Cacheable(cacheNames="search-ladder-stats")
    public Map<Integer, MergedLadderSearchStatsResult> findStats
    (
        Set<Region> regions,
        Set<League.LeagueType> leagueTypes,
        QueueType queueType,
        TeamType teamType
    )
    {
        MapSqlParameterSource params =
            ladderUtil.createSearchParams(0, regions, leagueTypes, queueType, teamType);
        Map<Integer, Map<Region, Map<BaseLeague.LeagueType, LadderSearchStatsResult>>> stats = template
            .query(LADDER_SEARCH_STATS_QUERY, params, LADDER_STATS_EXTRACTOR);
        Map<Integer, MergedLadderSearchStatsResult> result = new HashMap<>(stats.size(), 1.0f);
        for(Map.Entry<Integer, Map<Region, Map<BaseLeague.LeagueType, LadderSearchStatsResult>>> entry : stats.entrySet())
            result.put(entry.getKey(), new MergedLadderSearchStatsResult(entry.getValue()));
        return result;
    }

    @Cacheable(cacheNames="search-ladder-stats-bundle")
    public Map<QueueType, Map<TeamType, Map<Integer, MergedLadderSearchStatsResult>>> findStats()
    {
        Set<Region> regions = Set.of(Region.values());
        Set<BaseLeague.LeagueType> leagues = Set.of(BaseLeague.LeagueType.values());

        Map<QueueType, Map<TeamType, Map<Integer, MergedLadderSearchStatsResult>>> result = new EnumMap<>(QueueType.class);
        for(QueueType queueType: QueueType.values())
        {
            Map<TeamType, Map<Integer, MergedLadderSearchStatsResult>> teamMap = new EnumMap<>(TeamType.class);
            result.put(queueType, teamMap);
            for(TeamType teamType: TeamType.values())
            {
                teamMap.put(teamType, findStats(regions, leagues, queueType, teamType));
            }
        }
        return result;
    }

    @Cacheable(cacheNames="search-ladder-stats-queue")
    public List<QueueStats> findQueueStats(QueueType queueType, TeamType teamType)
    {
        return queueStatsDAO.findQueueStats(queueType, teamType);
    }

}
