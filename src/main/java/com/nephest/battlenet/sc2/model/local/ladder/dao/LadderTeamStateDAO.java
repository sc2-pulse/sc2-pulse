// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.local.dao.LeagueDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class LadderTeamStateDAO
{

    private static final String FIND_QUERY_TEMPLATE =
        "WITH team_filter AS "
        + "("
            + "SELECT team.id, "
            + "CASE "
                + "WHEN team_member.%1$s_games_played > 0 THEN %2$s "
                + "WHEN team_member.%3$s_games_played > 0 THEN %4$s "
                + "WHEN team_member.%5$s_games_played > 0 THEN %6$s "
                + "WHEN team_member.%7$s_games_played > 0 THEN %8$s "
            + "END AS race "
            + "FROM team "
            + "INNER JOIN team_member ON team.id = team_member.team_id "
            + "INNER JOIN player_character ON team_member.player_character_id = player_character.id "
            + "WHERE player_character.id = :playerCharacterId "
        + ") "
        + "SELECT team_filter.race, "
        + TeamStateDAO.STD_SELECT + ", "
        + "league_tier.type AS \"league_tier.type\", "
        + LeagueDAO.STD_SELECT + ", "
        + "season.battlenet_id as \"season.battlenet_id\" "
        + "FROM team_filter "
        + "INNER JOIN team_state ON team_filter.id = team_state.team_id "
        + "INNER JOIN division ON team_state.division_id = division.id "
        + "INNER JOIN league_tier ON division.league_tier_id = league_tier.id "
        + "INNER JOIN league ON league_tier.league_id = league.id "
        + "INNER JOIN season ON league.season_id = season.id "
        + "ORDER BY team_state.timestamp ASC";

    private static String FIND_QUERY;

    private static ResultSetExtractor<List<LadderTeamState>> STD_EXTRACTOR;

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    @Autowired
    public LadderTeamStateDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        initQueries(conversionService);
        initMappers(conversionService);
    }

    public static ResultSetExtractor<List<LadderTeamState>> getStdExtractor()
    {
        return STD_EXTRACTOR;
    }

    private static void initQueries(ConversionService conversionService)
    {
        FIND_QUERY = String.format(FIND_QUERY_TEMPLATE,
            "terran", conversionService.convert(Race.TERRAN, Integer.class),
            "protoss", conversionService.convert(Race.PROTOSS, Integer.class),
            "zerg", conversionService.convert(Race.ZERG, Integer.class),
            "random", conversionService.convert(Race.RANDOM, Integer.class)
        );
    }

    private static void initMappers(ConversionService conversionService)
    {
        if(STD_EXTRACTOR == null) STD_EXTRACTOR = (rs)->
        {
            List<LadderTeamState> result = new ArrayList<>();
            while(rs.next()) result.add(new LadderTeamState(
                TeamStateDAO.STD_ROW_MAPPER.mapRow(rs, 0),
                conversionService.convert(rs.getInt("race"), Race.class),
                conversionService.convert(rs.getInt("league_tier.type"), BaseLeagueTier.LeagueTierType.class),
                LeagueDAO.getStdRowMapper().mapRow(rs, 0),
                rs.getInt("season.battlenet_id")
            ));
            return result;
        };
    }

    public List<LadderTeamState> find(Long characterId)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("playerCharacterId", characterId);
        return template.query(FIND_QUERY, params, getStdExtractor());
    }

}
