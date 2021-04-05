// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.TeamMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class TeamDAO
{

    private  static final Logger LOG = LoggerFactory.getLogger(TeamDAO.class);

    public static final String STD_SELECT =
        "team.id AS \"team.id\", "
        + "team.league_tier_id AS \"team.league_tier_id\", "
        + "team.division_id AS \"team.division_id\", "
        + "team.battlenet_id AS \"team.battlenet_id\", "
        + "team.region AS \"team.region\", "
        + "team.rating AS \"team.rating\", "
        + "team.wins AS \"team.wins\", "
        + "team.losses AS \"team.losses\", "
        + "team.ties AS \"team.ties\", "
        + "team.points AS \"team.points\", "
        + "team.global_rank AS \"team.global_rank\", "
        + "team.region_rank AS \"team.region_rank\", "
        + "team.league_rank AS \"team.league_rank\" ";

    public static final String STD_ADDITIONAL_SELECT =
        "season.battlenet_id AS \"team.season\", "
        + "league.type AS \"team.league_type\", "
        + "league.queue_type AS \"team.queue_type\", "
        + "league.team_type AS \"team.team_type\", "
        + "league_tier.type AS \"team.tier_type\" ";

    private static final String CREATE_TEMPLATE = "INSERT INTO team "
        + "("
            + "%1$sleague_tier_id, division_id, battlenet_id, "
            + "region, "
            + "rating, points, wins, losses, ties"
        + ") "
        + "VALUES ("
            + "%2$s:leagueTierId, :divisionId, :battlenetId, "
            + ":region, "
            + ":rating, :points, :wins, :losses, :ties"
        + ")";

    private static final String CREATE_QUERY = String.format(CREATE_TEMPLATE, "", "");
    private static final String CREATE_WITH_ID_QUERY = String.format(CREATE_TEMPLATE, "id, ", ":id, ");

    private static final String MERGE_TEMPLATE =
        " "
        + "ON CONFLICT(%1$s) DO UPDATE SET "
        + "%2$s"
        + "league_tier_id=excluded.league_tier_id, "
        + "division_id=excluded.division_id, "
        + "rating=excluded.rating, "
        + "points=excluded.points, "
        + "wins=excluded.wins, "
        + "losses=excluded.losses, "
        + "ties=excluded.ties ";
    private static final String MERGE_CONDITION =
        "WHERE team.wins + team.losses + team.ties <> excluded.wins + excluded.losses + excluded.ties "
        + "OR team.division_id <> excluded.division_id";

    private static final String MERGE_QUERY = CREATE_QUERY
        + String.format(MERGE_TEMPLATE + MERGE_CONDITION, "region, battlenet_id", "");
    private static final String FORCE_MERGE_QUERY = CREATE_QUERY
        + String.format(MERGE_TEMPLATE, "region, battlenet_id", "");

    private static final String MERGE_BY_ID_QUERY = CREATE_WITH_ID_QUERY
        + String.format(MERGE_TEMPLATE + MERGE_CONDITION, "id",
            "region=excluded.region, battlenet_id = excluded.battlenet_id, ");
    private static final String FORCE_MERGE_BY_ID_QUERY = CREATE_WITH_ID_QUERY
        + String.format(MERGE_TEMPLATE, "id", "region=excluded.region, battlenet_id = excluded.battlenet_id, ");

    private static final String FIND_BY_ID_QUERY = "SELECT " + STD_SELECT + "FROM team WHERE id = :id";

    private static final String CALCULATE_RANK_TEMPLATE =
        "WITH cte AS"
        + "("
            + "SELECT team.id, RANK() OVER(PARTITION BY "
            + "league.queue_type, league.team_type%2$s ORDER BY team.rating DESC, team.id DESC) as rnk "
            + "FROM team "
            + "INNER JOIN league_tier ON league_tier.id = team.league_tier_id "
            + "INNER JOIN league ON league.id = league_tier.league_id "
            + "INNER JOIN season ON season.id = league.season_id "
            + "WHERE season.battlenet_id = :season "
            + "AND season.region IN (:regions) "
            + "AND league.queue_type IN (:queueTypes) "
            + "AND league.team_type IN (:teamTypes) "
            + "AND league.type IN (:leagueTypes) "
        + ")"
        + "UPDATE team "
        + "set %1$s_rank=cte.rnk "
        + "FROM cte "
        + "WHERE team.id = cte.id "
        + "AND team.%1$s_rank != cte.rnk";

    private static final Map<Race, String> FIND_1V1_TEAM_BY_FAVOURITE_RACE_QUERIES = new EnumMap<>(Race.class);

    private static final String CALCULATE_GLOBAL_RANK_QUERY = String.format(CALCULATE_RANK_TEMPLATE, "global", "");
    private static final String CALCULATE_REGION_RANK_QUERY =
        String.format(CALCULATE_RANK_TEMPLATE, "region", ", season.region");
    private static final String CALCULATE_LEAGUE_RANK_QUERY =
        String.format(CALCULATE_RANK_TEMPLATE, "league", ", league.type");

    private static RowMapper<Team> STD_ROW_MAPPER;
    private static ResultSetExtractor<Team> STD_EXTRACTOR;

    public static final ResultSetExtractor<Optional<Map.Entry<Team, List<TeamMember>>>> BY_FAVOURITE_RACE_EXTRACTOR =
    (rs)->
    {
        if(!rs.next()) return Optional.empty();

        return Optional.of(new AbstractMap.SimpleEntry<Team,List<TeamMember>>(
            TeamDAO.getStdRowMapper().mapRow(rs, 0),
            List.of(TeamMemberDAO.STD_ROW_MAPPER.mapRow(rs, 0))));
    };


    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    @Autowired
    public TeamDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        initMappers(conversionService);
        initQueries(conversionService);
    }

    private static void initMappers(ConversionService conversionService)
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, i)->
        {
            BigDecimal idDec = (BigDecimal) rs.getObject("team.battlenet_id");
            return new Team
            (
                rs.getLong("team.id"),
                conversionService.convert(rs.getInt("team.region"), Region.class),
                rs.getInt("team.league_tier_id"),
                rs.getInt("team.division_id"),
                idDec == null ? null : idDec.toBigInteger(),
                rs.getLong("team.rating"),
                rs.getInt("team.wins"), rs.getInt("team.losses"), rs.getInt("team.ties"),
                rs.getInt("team.points")
            );
        };
        if(STD_EXTRACTOR == null) STD_EXTRACTOR = (rs)->
        {
            if(!rs.next()) return null;
            return getStdRowMapper().mapRow(rs, 0);
        };
    }

    private static void initQueries(ConversionService conversionService)
    {
        if(FIND_1V1_TEAM_BY_FAVOURITE_RACE_QUERIES.isEmpty())
        {
            String template =
                "SELECT " + TeamDAO.STD_SELECT + ", " + TeamMemberDAO.STD_SELECT
                    + "FROM team_member "
                    + "INNER JOIN team ON team_member.team_id = team.id "
                    + "INNER JOIN league_tier ON league_tier.id = team.league_tier_id "
                    + "INNER JOIN league ON league.id = league_tier.league_id "
                    + "INNER JOIN season ON season.id = league.season_id "
                    + "WHERE team_member.player_character_id = :playerCharacterId "
                    + "AND team_member.%1$s_games_played > 0 "
                    + "AND season.battlenet_id = :season "
                    + "AND season.region = :region "
                    + "AND league.queue_type = " + conversionService.convert(QueueType.LOTV_1V1, Integer.class) + " "
                    + "AND league.team_type = " + conversionService.convert(TeamType.ARRANGED, Integer.class);
            for(Race race : Race.values()) FIND_1V1_TEAM_BY_FAVOURITE_RACE_QUERIES.put(
                race, String.format(template, race.getName().toLowerCase()));
        }
    }

    public static RowMapper<Team> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    public static ResultSetExtractor<Team> getStdExtractor()
    {
        return STD_EXTRACTOR;
    }

    public Team create(Team team)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = createParameterSource(team);
        template.update(CREATE_QUERY, params, keyHolder, new String[]{"id"});
        team.setId(keyHolder.getKey().longValue());
        return team;
    }

    /*
        Profile ladders do not have last played field, so the only way to filter out the teams is comparing total
        games played when inserting a team into the db.
        Returning nulls here to tell that there were no modifications made, so the update chain could be interrupted
     */
    public Team merge(Team team, boolean force)
    {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = createParameterSource(team);
        int updated = template.update(force ? FORCE_MERGE_QUERY : MERGE_QUERY, params, keyHolder, new String[]{"id"});
        if(updated > 0)
        {
            team.setId(keyHolder.getKey().longValue());
            return team;
        }
        return null;
    }

    public Team merge(Team team)
    {
        return merge(team, false);
    }

    public Team mergeById(Team team, boolean force)
    {
        MapSqlParameterSource params = createParameterSource(team);
        params.addValue("id", team.getId());
        if(template.update(force ? FORCE_MERGE_BY_ID_QUERY : MERGE_BY_ID_QUERY, params) > 0) return team;
        return null;
    }

    //this method is intended to be used with legacy teams
    public Team mergeByFavoriteRace
    (Team team, int season, PlayerCharacter character, Race race)
    {
        Map.Entry<Team, List<TeamMember>> foundTeam = find1v1TeamByFavoriteRace(season, character, race).orElse(null);

        if(foundTeam == null) return merge(team);
        //legacy team can have invalid natural id
        team.setId(foundTeam.getKey().getId());
        if(foundTeam.getKey().getBattlenetId() != null) team.setBattlenetId(foundTeam.getKey().getBattlenetId());
        if(Team.shouldUpdate(foundTeam.getKey(), team)) return mergeById(team, false);

        return team;
    }

    public Optional<Team> findById(long id)
    {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id);
        return Optional.ofNullable(template.query(FIND_BY_ID_QUERY, params, getStdExtractor()));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void updateRanks
    (int season, Region[] regions, QueueType[] queues, TeamType[] teams, BaseLeague.LeagueType[] leagues)
    {
        Set<Integer> regionsInt = Arrays.stream(regions)
            .map(r->conversionService.convert(r, Integer.class))
            .collect(Collectors.toSet());
        Set<Integer> queuesInt = Arrays.stream(queues)
            .map(q->conversionService.convert(q, Integer.class))
            .collect(Collectors.toSet());
        Set<Integer> teamsInt = Arrays.stream(teams)
            .map(t->conversionService.convert(t, Integer.class))
            .collect(Collectors.toSet());
        Set<Integer> leaguesInt = Arrays.stream(leagues)
            .map(l->conversionService.convert(l, Integer.class))
            .collect(Collectors.toSet());
        updateGlobalRanks(season, queuesInt, teamsInt);
        updateRegionRanks(season, regionsInt, queuesInt, teamsInt);
        updateLeagueRanks(season, leaguesInt, queuesInt, teamsInt);

        LOG.debug("Calculated team ranks for {} season", season);
    }

    private void updateGlobalRanks(int season, Set<Integer> queues, Set<Integer> teams)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("season", season)
            .addValue("regions", Arrays.stream(Region.values())
                .map(r->conversionService.convert(r, Integer.class))
                .collect(Collectors.toSet()))
            .addValue("queueTypes", queues)
            .addValue("teamTypes", teams)
            .addValue("leagueTypes", Arrays.stream(BaseLeague.LeagueType.values())
                .map(l->conversionService.convert(l, Integer.class))
                .collect(Collectors.toSet()));
        template.update(CALCULATE_GLOBAL_RANK_QUERY, params);
    }

    private void updateRegionRanks(int season, Set<Integer> regions, Set<Integer> queues, Set<Integer> teams)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("season", season)
            .addValue("regions", regions)
            .addValue("queueTypes", queues)
            .addValue("teamTypes", teams)
            .addValue("leagueTypes", Arrays.stream(BaseLeague.LeagueType.values())
                .map(l->conversionService.convert(l, Integer.class))
                .collect(Collectors.toSet()));
        template.update(CALCULATE_REGION_RANK_QUERY, params);
    }

    private void updateLeagueRanks(int season, Set<Integer> leagues, Set<Integer> queues, Set<Integer> teams)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("season", season)
            .addValue("regions", Arrays.stream(Region.values())
                .map(r->conversionService.convert(r, Integer.class))
                .collect(Collectors.toSet()))
            .addValue("queueTypes", queues)
            .addValue("teamTypes", teams)
            .addValue("leagueTypes", leagues);
        template.update(CALCULATE_LEAGUE_RANK_QUERY, params);
    }

    public Optional<Map.Entry<Team, List<TeamMember>>> find1v1TeamByFavoriteRace
    (
        int season,
        PlayerCharacter playerCharacter,
        Race race
    )
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("season", season)
            .addValue("region", conversionService.convert(playerCharacter.getRegion(), Integer.class))
            .addValue("playerCharacterId", playerCharacter.getId());
        return template.query(FIND_1V1_TEAM_BY_FAVOURITE_RACE_QUERIES.get(race), params, BY_FAVOURITE_RACE_EXTRACTOR);
    }

    private MapSqlParameterSource createParameterSource(Team team)
    {
        return new MapSqlParameterSource()
            .addValue("leagueTierId", team.getLeagueTierId())
            .addValue("divisionId", team.getDivisionId())
            .addValue("battlenetId", team.getBattlenetId())
            .addValue("region", conversionService.convert(team.getRegion(), Integer.class))
            .addValue("rating", team.getRating())
            .addValue("points", team.getPoints())
            .addValue("wins", team.getWins())
            .addValue("losses", team.getLosses())
            .addValue("ties", team.getTies());
    }

}
