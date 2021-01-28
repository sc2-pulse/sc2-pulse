// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.Division;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Repository
public class DivisionDAO
{

    public static final String STD_SELECT =
        "division.id AS \"division.id\", "
        + "division.league_tier_id AS \"division.league_tier_id\", "
        + "division.battlenet_id AS \"division.battlenet_id\" ";

    private static final String CREATE_TEMPLATE = "INSERT INTO division "
        + "(%1$sleague_tier_id, battlenet_id) "
        + "VALUES (%2$s:leagueTierId, :battlenetId)";

    private static final String CREATE_QUERY = String.format(CREATE_TEMPLATE, "", "");
    private static final String CREATE_WITH_ID_QUERY = String.format(CREATE_TEMPLATE, "id, ", ":id, ");

    private static final String MERGE_QUERY = CREATE_QUERY
        + " "
        + "ON CONFLICT(league_tier_id, battlenet_id) DO UPDATE SET "
        + "battlenet_id=excluded.battlenet_id";

    private static final String MERGE_BY_ID_QUERY = CREATE_WITH_ID_QUERY
        + " "
        + "ON CONFLICT(id) DO UPDATE SET "
        + "battlenet_id=excluded.battlenet_id,"
        + "league_tier_id=excluded.league_tier_id";

    private static final String FIND_LIST_BY_LADDER =
        "SELECT " + STD_SELECT
        + "FROM division "
        + "INNER JOIN league_tier ON division.league_tier_id=league_tier.id "
        + "INNER JOIN league ON league_tier.league_id=league.id "
        + "INNER JOIN season ON league.season_id=season.id "
        + "WHERE season.battlenet_id=:seasonBattlenetId AND season.region=:region "
        + "AND league.type=:leagueType AND league.queue_type=:queueType AND league.team_type=:teamType "
        + "AND league_tier.type=:tierType";

    private static final String FIND_PROFILE_IDS =
        "WITH team_filter AS "
        + "( "
            + "SELECT DISTINCT ON (team.division_id) team.id, team.division_id "
            + "FROM team "
            + "WHERE "
            + "team.season=:season "
            + "AND team.region=:region "
            + "AND team.league_type IN (:leagues) "
            + "AND team.queue_type=:queueType "
            + "AND team.team_type=:teamType "
            //select a player who is less likely to promote to next division
            + "ORDER BY team.division_id DESC, team.rating DESC"
        + "), "
        + "team_member_filter AS "
        + "( "
            + "SELECT DISTINCT ON (team_filter.id) team_member.player_character_id, team_filter.division_id "
            + "FROM team_filter "
            + "INNER JOIN team_member ON team_filter.id = team_member.team_id "
        + ") "
        + "SELECT " + STD_SELECT + ", " + PlayerCharacterDAO.STD_SELECT
        + "FROM team_member_filter "
        + "INNER JOIN division ON team_member_filter.division_id = division.id "
        + "INNER JOIN player_character ON team_member_filter.player_character_id = player_character.id ORDER BY " 
        + "division.battlenet_id";
    private static final String FIND_DIVISION_BATTLENET_IDS =
        "SELECT division.battlenet_id "
        + "FROM division "
        + "INNER JOIN league_tier ON division.league_tier_id = league_tier.id "
        + "INNER JOIN league ON league_tier.league_id = league.id "
        + "INNER JOIN season ON league.season_id = season.id "
        + "WHERE "
        + "season.battlenet_id=:season "
        + "AND season.region=:region "
        + "AND league.type IN (:leagues) "
        + "AND league.queue_type = :queueType "
        + "AND league.team_type = :teamType ";

    private static final String FIND_LAST_DIVISION_BATTLENET_ID =
        "SELECT MAX(division.battlenet_id) "
        + "FROM division "
        + "INNER JOIN league_tier ON division.league_tier_id = league_tier.id "
        + "INNER JOIN league ON league_tier.league_id = league.id "
        + "INNER JOIN season ON league.season_id = season.id "
        + "WHERE "
        + "season.battlenet_id=:season "
        + "AND season.region=:region "
        + "AND league.queue_type = :queueType "
        + "AND league.team_type = :teamType";

    private static final String FIND_DIVISION =
        "SELECT " + STD_SELECT
        + "FROM division "
        + "INNER JOIN league_tier ON division.league_tier_id = league_tier.id "
        + "INNER JOIN league ON league_tier.league_id = league.id "
        + "INNER JOIN season ON league.season_id = season.id "
        + "WHERE "
        + "season.battlenet_id=:season "
        + "AND season.region=:region "
        + "AND league.queue_type = :queueType "
        + "AND league.team_type = :teamType "
        + "AND division.battlenet_id=:divisionBattlenetId";
    private static final String FIND_DIVISION_COUNT =
        "SELECT COUNT(*) "
        + "FROM division "
        + "INNER JOIN league_tier ON division.league_tier_id = league_tier.id "
        + "INNER JOIN league ON league_tier.league_id = league.id "
        + "INNER JOIN season ON league.season_id = season.id "
        + "WHERE "
        + "season.battlenet_id=:season "
        + "AND season.region=:region "
        + "AND league.type IN (:leagues) "
        + "AND league.queue_type = :queueType "
        + "AND league.team_type = :teamType ";

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    public static final RowMapper<Division> STD_ROW_MAPPER = (rs, num)-> new Division
    (
        rs.getLong("division.id"),
        rs.getLong("division.league_tier_id"),
        rs.getLong("division.battlenet_id")
    );

    public static final ResultSetExtractor<Division> STD_EXTRACTOR = (rs)->
    {
        if(!rs.next()) return null;
        return STD_ROW_MAPPER.mapRow(rs, 0);
    };

    public static final ResultSetExtractor<Map<Division, PlayerCharacter>> PROFILE_IDS_EXTRACTOR = (rs)->
    {
        Map<Division, PlayerCharacter> result = new HashMap<>();
        while(rs.next())
            result.put(STD_ROW_MAPPER.mapRow(rs, 0), PlayerCharacterDAO.getStdRowMapper().mapRow(rs, 0));
        return result;
    };

    @Autowired
    public DivisionDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
    }

    public Division create(Division division)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = createPamarameterSource(division);
        template.update(CREATE_QUERY, params, keyHolder, new String[]{"id"});
        division.setId(keyHolder.getKey().longValue());
        return division;
    }

    public Division merge(Division division)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = createPamarameterSource(division);
        template.update(MERGE_QUERY, params, keyHolder, new String[]{"id"});
        division.setId(keyHolder.getKey().longValue());
        return division;
    }

    public Division mergeById(Division division)
    {
        MapSqlParameterSource params = createPamarameterSource(division);
        params.addValue("id", division.getId());
        template.update(MERGE_BY_ID_QUERY, params);
        return division;
    }

    private MapSqlParameterSource createPamarameterSource(Division division)
    {
        return new MapSqlParameterSource()
            .addValue("leagueTierId", division.getTierId())
            .addValue("battlenetId", division.getBattlenetId());
    }

    public List<Division> findListByLadder
    (
        Integer season, Region region,
        BaseLeague.LeagueType leagueType, QueueType queueType, TeamType teamType,
        BaseLeagueTier.LeagueTierType tierType
    )
    {
        return template.query
        (
            FIND_LIST_BY_LADDER,
            new MapSqlParameterSource()
                .addValue("seasonBattlenetId", season)
                .addValue("region", conversionService.convert(region, Integer.class))
                .addValue("leagueType", conversionService.convert(leagueType, Integer.class))
                .addValue("queueType", conversionService.convert(queueType, Integer.class))
                .addValue("teamType", conversionService.convert(teamType, Integer.class))
                .addValue("tierType", conversionService.convert(tierType, Integer.class)),
            STD_ROW_MAPPER
        );
    }

    public Optional<Division> findDivision
    (
        int season,
        Region region,
        QueueType queueType,
        TeamType teamType,
        long battlenetId
    )
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("season", season)
            .addValue("region", conversionService.convert(region, Integer.class))
            .addValue("queueType", conversionService.convert(queueType, Integer.class))
            .addValue("teamType", conversionService.convert(teamType, Integer.class))
            .addValue("divisionBattlenetId", battlenetId);
        return Optional.ofNullable(template.query(FIND_DIVISION, params, DivisionDAO.STD_EXTRACTOR));
    }

    public Optional<Long> findLastDivision
    (
        int season,
        Region region,
        QueueType queueType,
        TeamType teamType
    )
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("season", season)
            .addValue("region", conversionService.convert(region, Integer.class))
            .addValue("queueType", conversionService.convert(queueType, Integer.class))
            .addValue("teamType", conversionService.convert(teamType, Integer.class));
        return Optional.ofNullable(template.query(FIND_LAST_DIVISION_BATTLENET_ID, params, DAOUtils.LONG_EXTRACTOR));
    }

    public Map<Division, PlayerCharacter> findProfileDivisionIds
    (
        int season,
        Region region,
        BaseLeague.LeagueType[] leagues,
        QueueType queueType,
        TeamType teamType
    )
    {
        Set<Integer> leagueInts = new HashSet<>(leagues.length, 1F);
        for (BaseLeague.LeagueType league : leagues) leagueInts.add(conversionService.convert(league, Integer.class));
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("season", season)
            .addValue("region", conversionService.convert(region, Integer.class))
            .addValue("leagues", leagueInts)
            .addValue("queueType", conversionService.convert(queueType, Integer.class))
            .addValue("teamType", conversionService.convert(teamType, Integer.class));
        return template.query(FIND_PROFILE_IDS, params, PROFILE_IDS_EXTRACTOR);
    }

    public List<Long> findDivisionIds
    (
        int season,
        Region region,
        BaseLeague.LeagueType[] leagues,
        QueueType queueType,
        TeamType teamType
    )
    {
        Set<Integer> leagueInts = Arrays.stream(leagues)
            .map(l->conversionService.convert(l, Integer.class))
            .collect(Collectors.toSet());
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("season", season)
            .addValue("region", conversionService.convert(region, Integer.class))
            .addValue("leagues", leagueInts)
            .addValue("queueType", conversionService.convert(queueType, Integer.class))
            .addValue("teamType", conversionService.convert(teamType, Integer.class));
        return template.query(FIND_DIVISION_BATTLENET_IDS, params, DAOUtils.LONG_MAPPER);
    }

    public int getDivisionCount
    (
        int season,
        Region region,
        BaseLeague.LeagueType[] leagues,
        QueueType queueType,
        TeamType teamType
    )
    {
        Set<Integer> leagueInts = Arrays.stream(leagues)
            .map(l->conversionService.convert(l, Integer.class))
            .collect(Collectors.toSet());
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("season", season)
            .addValue("region", conversionService.convert(region, Integer.class))
            .addValue("leagues", leagueInts)
            .addValue("queueType", conversionService.convert(queueType, Integer.class))
            .addValue("teamType", conversionService.convert(teamType, Integer.class));
        return template.query(FIND_DIVISION_COUNT, params, DAOUtils.INT_EXTRACTOR);
    }

}
