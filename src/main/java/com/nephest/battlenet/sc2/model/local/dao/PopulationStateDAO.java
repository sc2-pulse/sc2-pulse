// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.PlayerCharacterReport;
import com.nephest.battlenet.sc2.model.local.PopulationState;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PopulationStateDAO
{

    public static final String STD_SELECT =
        "population_state.id AS \"population_state.id\", "
        + "population_state.league_id AS \"population_state.league_id\", "
        + "population_state.global_team_count AS \"population_state.global_team_count\", "
        + "population_state.region_team_count AS \"population_state.region_team_count\", "
        + "population_state.league_team_count AS \"population_state.league_team_count\" ";

    public static final String TEAM_DATA_SELECT =
        "population_state.global_team_count AS \"population_state.global_team_count\", "
        + "population_state.region_team_count AS \"population_state.region_team_count\", "
        + "population_state.league_team_count AS \"population_state.league_team_count\" ";

    private static final String TAKE_SNAPSHOT =
        "WITH "
        + "cheaters_league AS "
        + "( "
            + String.format
            (
                TeamDAO.FIND_CHEATER_TEAMS_BY_SEASONS_TEMPLATE,
                "COUNT(DISTINCT(team.id)) as count, season AS battlenet_id, region, queue_type, team_type, "
                + "league_type AS type"
            )
            + " "
            + "GROUP BY season, region, queue_type, team_type, team.league_type "
        + "), "
        + "cheaters_region AS "
        + "( "
            + String.format
            (
                TeamDAO.FIND_CHEATER_TEAMS_BY_SEASONS_TEMPLATE,
                "COUNT(DISTINCT(team.id)) as count, season AS battlenet_id, region, queue_type, team_type"
            )
            + " "
            + "GROUP BY season, region, queue_type, team_type"
        + "), "
        + "cheaters_global AS "
        + "( "
            + "SELECT battlenet_id AS season, queue_type, team_type, SUM(count) AS count "
            + "FROM cheaters_region "
            + "GROUP BY battlenet_id, queue_type, team_type "
        + "), "
        + "league_team_count AS "
        + "("
            + "SELECT season.battlenet_id AS season, queue_type, team_type, season.region, "
            + "MAX(type) AS type, league.id AS league_id, "
            + "SUM(team_count) - COALESCE(MAX(cheaters_league.count), 0) AS count "
            + "FROM league_stats "
            + "INNER JOIN league ON league_stats.league_id = league.id "
            + "INNER JOIN season ON league.season_id = season.id "
            + "LEFT JOIN cheaters_league USING(battlenet_id, region, queue_type, team_type, type) "
            + "WHERE season.battlenet_id IN(:seasons) "
            + "GROUP BY season.battlenet_id, season.region, queue_type, team_type, league.id "
        + "), "
        + "region_team_count AS "
        + "("
            + "SELECT season.battlenet_id AS season, queue_type, team_type, region, "
            + "SUM(team_count) - COALESCE(MAX(cheaters_region.count), 0) AS count,  "
            + "SUM(team_count) AS count_original "
            + "FROM league_stats "
            + "INNER JOIN league ON league_stats.league_id = league.id "
            + "INNER JOIN season ON league.season_id = season.id "
            + "LEFT JOIN cheaters_region USING(battlenet_id, region ,queue_type, team_type) "
            + "WHERE season.battlenet_id IN(:seasons) "
            + "GROUP BY season.battlenet_id, season.region, queue_type, team_type "
        + "), "
        + "global_team_count AS "
        + "("
            + "SELECT season, queue_type, team_type, "
            + "SUM(region_team_count.count_original) - COALESCE(MAX(cheaters_global.count), 0) AS count  "
            + "FROM region_team_count "
            + "LEFT JOIN cheaters_global USING(season, queue_type, team_type) "
            + "GROUP BY season, queue_type, team_type "
        + ") "
        + "INSERT INTO "
        + "population_state"
        + "(league_id, global_team_count, region_team_count, league_team_count) "
        + "SELECT league_team_count.league_id, "
        + "global_team_count.count, "
        + "region_team_count.count, "
        + "league_team_count.count "
        + "FROM league_team_count "
        + "INNER JOIN region_team_count "
            + "ON league_team_count.season = region_team_count.season "
            + "AND league_team_count.region = region_team_count.region "
            + "AND league_team_count.queue_type = region_team_count.queue_type "
            + "AND league_team_count.team_type = region_team_count.team_type "
        + "INNER JOIN global_team_count "
            + "ON league_team_count.season = global_team_count.season "
            + "AND league_team_count.queue_type = global_team_count.queue_type "
            + "AND league_team_count.team_type = global_team_count.team_type";

    private static final String FIND_BY_IDS = "SELECT " + STD_SELECT
        + "FROM population_state "
        + "WHERE id IN(:ids)";

    public static final RowMapper<PopulationState> STD_ROW_MAPPER = (rs, i)->new PopulationState
    (
        rs.getInt("population_state.id"),
        rs.getInt("population_state.league_id"),
        rs.getInt("population_state.global_team_count"),
        rs.getInt("population_state.region_team_count"),
        DAOUtils.getInteger(rs, "population_state.league_team_count")
    );

    public static final RowMapper<PopulationState> TEAM_DATA_ROW_MAPPER = (rs, i)->PopulationState.teamDataOnly
    (
        DAOUtils.getInteger(rs, "population_state.global_team_count"),
        DAOUtils.getInteger(rs, "population_state.region_team_count"),
        DAOUtils.getInteger(rs, "population_state.league_team_count")
    );

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;
    private final SeasonDAO seasonDAO;

    @Autowired
    public PopulationStateDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService,
        SeasonDAO seasonDAO
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        this.seasonDAO = seasonDAO;
    }

    /**
     * <p>
     *     Creates full population snapshot. It is mainly used in teams.
     * </p>
     * @param seasons target seasons
     * @return number of created snapshots
     */
    public int takeSnapshot(Collection<Integer> seasons)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("seasons", seasons)
            .addValue
            (
                "cheaterReportType",
                conversionService.convert(PlayerCharacterReport.PlayerCharacterReportType.CHEATER, Integer.class)
            );

        return template.update(TAKE_SNAPSHOT, params);
    }

    public List<PopulationState> findByIds(Collection<Integer> ids)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("ids", ids);
        return template.query(FIND_BY_IDS, params, STD_ROW_MAPPER);
    }

}
