// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.local.dao.DAOUtils;
import com.nephest.battlenet.sc2.model.local.dao.LeagueDAO;
import com.nephest.battlenet.sc2.model.local.dao.PopulationStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamState;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LadderTeamStateDAO
{

    private static final String SELECT_QUERY_PART =
        "SELECT team_filter.race, "
        + TeamStateDAO.SHORT_SELECT + ", "
        + PopulationStateDAO.TEAM_DATA_SELECT + ", "
        + "league_tier.type AS \"league_tier.type\", "
        + LeagueDAO.SHORT_SELECT + ", "
        + "season.battlenet_id as \"season.battlenet_id\" "
        + "FROM team_filter "
        + "INNER JOIN team_state ON team_filter.id = team_state.team_id "
        + "LEFT JOIN population_state "
            + "ON team_state.population_state_id = population_state.id "
        + "INNER JOIN division ON team_state.division_id = division.id "
        + "INNER JOIN league_tier ON division.league_tier_id = league_tier.id "
        + "INNER JOIN league ON league_tier.league_id = league.id "
        + "INNER JOIN season ON league.season_id = season.id ";

    private static final String FIND_BY_CHARACTER_ID_QUERY_TEMPLATE =
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
        + SELECT_QUERY_PART;

    private static final String FIND_BY_LEGACY_ID =
        "WITH team_filter AS "
        + "(SELECT id, NULL AS \"race\" FROM team WHERE (team.queue_type, team.region, team.legacy_id) IN (:legacyUids) )"
        + SELECT_QUERY_PART
        + "ORDER BY team_state.timestamp ASC ";

    private static String FIND_QUERY;
    private static String FIND_FROM_QUERY;

    private static RowMapper<LadderTeamState> STD_ROW_MAPPER;
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

    public static RowMapper<LadderTeamState> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    public static ResultSetExtractor<List<LadderTeamState>> getStdExtractor()
    {
        return STD_EXTRACTOR;
    }

    private static void initQueries(ConversionService conversionService)
    {
        FIND_QUERY = String.format(FIND_BY_CHARACTER_ID_QUERY_TEMPLATE
            + "ORDER BY team_state.timestamp ASC ",
            "terran", conversionService.convert(Race.TERRAN, Integer.class),
            "protoss", conversionService.convert(Race.PROTOSS, Integer.class),
            "zerg", conversionService.convert(Race.ZERG, Integer.class),
            "random", conversionService.convert(Race.RANDOM, Integer.class)
        );
        FIND_FROM_QUERY = String.format(FIND_BY_CHARACTER_ID_QUERY_TEMPLATE
            + "WHERE (:from::timestamp with time zone IS NULL OR team_state.timestamp >= :from) "
            + "ORDER BY team_state.timestamp ASC ",
            "terran", conversionService.convert(Race.TERRAN, Integer.class),
            "protoss", conversionService.convert(Race.PROTOSS, Integer.class),
            "zerg", conversionService.convert(Race.ZERG, Integer.class),
            "random", conversionService.convert(Race.RANDOM, Integer.class)
        );
    }

    private static void initMappers(ConversionService conversionService)
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, i)-> new LadderTeamState
        (
            TeamStateDAO.SHORT_ROW_MAPPER.mapRow(rs, 0),
            DAOUtils.getConvertedObjectFromInteger(rs, "race", conversionService, Race.class),
            conversionService.convert
            (
                DAOUtils.getInteger(rs, "league_tier.type"),
                BaseLeagueTier.LeagueTierType.class
            ),
            LeagueDAO.getShortRowMapper().mapRow(rs, 0),
            rs.getInt("season.battlenet_id"),
            PopulationStateDAO.TEAM_DATA_ROW_MAPPER.mapRow(rs, 0)
        );

        if(STD_EXTRACTOR == null) STD_EXTRACTOR = (rs)->
        {
            List<LadderTeamState> result = new ArrayList<>();
            while(rs.next()) result.add(getStdRowMapper().mapRow(rs, 0));
            return result;
        };
    }

    public List<LadderTeamState> find(Long characterId)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("playerCharacterId", characterId);
        return template.query(FIND_QUERY, params, getStdExtractor());
    }

    public List<LadderTeamState> find(Long characterId, OffsetDateTime from)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("playerCharacterId", characterId)
            .addValue("from", from);
        return template.query(FIND_FROM_QUERY, params, getStdExtractor());
    }

    public List<LadderTeamState> find(Set<TeamLegacyUid> ids)
    {
        if(ids.isEmpty()) return new ArrayList<>();

        List<Object[]> legacyUids = ids.stream()
            .map(id->new Object[]{
                conversionService.convert(id.getQueueType(), Integer.class),
                conversionService.convert(id.getRegion(), Integer.class),
                id.getId()
            })
           .collect(Collectors.toList());
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("legacyUids", legacyUids);
        return template.query(FIND_BY_LEGACY_ID, params, getStdExtractor());
    }

}
