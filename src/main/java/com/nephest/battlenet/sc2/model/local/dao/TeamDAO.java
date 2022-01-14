// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.blizzard.*;
import com.nephest.battlenet.sc2.model.local.*;
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
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class TeamDAO
{

    private  static final Logger LOG = LoggerFactory.getLogger(TeamDAO.class);

    public static final String STD_SELECT =
        "team.id AS \"team.id\", "
        + "team.legacy_id AS \"team.legacy_id\", "
        + "team.division_id AS \"team.division_id\", "
        + "team.season AS \"team.season\", "
        + "team.region AS \"team.region\", "
        + "team.league_type AS \"team.league_type\", "
        + "team.queue_type AS \"team.queue_type\", "
        + "team.team_type AS \"team.team_type\", "
        + "team.tier_type AS \"team.tier_type\", "
        + "team.rating AS \"team.rating\", "
        + "team.wins AS \"team.wins\", "
        + "team.losses AS \"team.losses\", "
        + "team.ties AS \"team.ties\", "
        + "team.points AS \"team.points\", "
        + "team.global_rank AS \"team.global_rank\", "
        + "team.region_rank AS \"team.region_rank\", "
        + "team.league_rank AS \"team.league_rank\" ";

    private static final String CREATE_TEMPLATE = "INSERT INTO team "
        + "("
            + "%1$slegacy_id, division_id, "
            + "season, region, league_type, queue_type, team_type, tier_type, "
            + "rating, points, wins, losses, ties"
        + ") "
        + "VALUES ("
            + "%2$s:legacyId, :divisionId, "
            + ":season, :region, :leagueType, :queueType, :teamType, :tierType, "
            + ":rating, :points, :wins, :losses, :ties"
        + ")";

    private static final String CREATE_QUERY = String.format(CREATE_TEMPLATE, "", "");

    private static final String MERGE_CLAUSE =
        " "
         + "ON CONFLICT(queue_type, region, legacy_id, season) DO UPDATE SET "
        + "division_id=excluded.division_id, "
        + "league_type=excluded.league_type, "
        + "tier_type=excluded.tier_type, "
        + "rating=excluded.rating, "
        + "points=excluded.points, "
        + "wins=excluded.wins, "
        + "losses=excluded.losses, "
        + "ties=excluded.ties ";

    private static final String MERGE_BY_FAVORITE_RACE_QUERY =
        "WITH "
        + "vals AS(VALUES :teams), "
        + "existing AS "
        + "("
            + "SELECT team.queue_type, "
            + "team.id, "
            + "team.region, "
            + "team.legacy_id, "
            + "team.season "
            + "FROM team "
            + "INNER JOIN vals v"
            + "("
                + "legacy_id, division_id, "
                + "season, region, league_type, queue_type, team_type, "
                + "rating, points, wins, losses, ties, tier_type "
            + ") "
                + "USING(queue_type, region, legacy_id, season) "
        + "), "
        + "updated AS "
        + "("
            + "UPDATE team "
            + "SET "
            + "division_id=v.division_id, "
            + "league_type=v.league_type, "
            + "tier_type=v.tier_type::smallint, "
            + "rating=v.rating, "
            + "points=v.points, "
            + "wins=v.wins, "
            + "losses=v.losses, "
            + "ties=v.ties "
            + "FROM vals v"
            + "("
                + "legacy_id, division_id, "
                + "season, region, league_type, queue_type, team_type, "
                + "rating, points, wins, losses, ties, tier_type "
            + ") "
            + "WHERE team.queue_type = v.queue_type "
            + "AND team.region = v.region "
            + "AND team.legacy_id = v.legacy_id "
            + "AND team.season = v.season "
            + "AND "
            + "("
                + "team.division_id != v.division_id OR "
                + "(team.wins + team.losses + team.ties) != (v.wins + v.losses + v.ties) "
            + ") "
            + "RETURNING " + STD_SELECT
        + "), "
        + "missing AS "
        + "("
            + "SELECT "
            + "v.legacy_id, v.division_id, "
            + "v.season, v.region, v.league_type, v.queue_type, v.team_type, "
            + "v.rating, v.points, v.wins, v.losses, v.ties, v.tier_type::smallint "
            + "FROM vals v"
            + "("
                + "legacy_id, division_id, "
                + "season, region, league_type, queue_type, team_type, "
                + "rating, points, wins, losses, ties, tier_type "
            + ") "
            + "LEFT JOIN existing ON "
                + "v.queue_type = existing.queue_type "
                + "AND v.region = existing.region "
                + "AND v.legacy_id = existing.legacy_id "
                + "AND v.season = existing.season "
            + "WHERE existing.id IS NULL"
        + "), "
        + "inserted AS "
        + "("
            + "INSERT INTO team "
            + "("
                + "legacy_id, division_id, "
                + "season, region, league_type, queue_type, team_type, "
                + "rating, points, wins, losses, ties, tier_type "
            + ") "
            + "SELECT * FROM missing "
            + MERGE_CLAUSE
            + "RETURNING " + STD_SELECT
        + ") "
        + "SELECT * FROM updated "
        + "UNION "
        + "SELECT * FROM inserted";
    private static final String FIND_BY_ID_QUERY = "SELECT " + STD_SELECT + "FROM team WHERE id = :id";

    public static final String FIND_CHEATER_TEAMS_BY_SEASONS_TEMPLATE =
        "SELECT %1$s "
        + "FROM team "
        + "INNER JOIN team_member ON team.id = team_member.team_id "
        + "INNER JOIN player_character_report AS confirmed_cheater_report "
        + "ON team_member.player_character_id = confirmed_cheater_report.player_character_id "
        + "AND confirmed_cheater_report.type = :cheaterReportType "
        + "AND confirmed_cheater_report.status = true "
        + "WHERE team.season IN(:seasons)";

    private static final String FIND_CHEATER_TEAM_IDS_BY_SEASON_QUERY =
        String.format(FIND_CHEATER_TEAMS_BY_SEASONS_TEMPLATE, "DISTINCT(team_id)");

    private static final String CALCULATE_RANK_QUERY =
        "WITH "
        + "cheaters AS "
        + "( "
            + FIND_CHEATER_TEAM_IDS_BY_SEASON_QUERY
        + "), "
        + "ranks AS "
        + "( "
            + "SELECT id, "
            + "RANK() OVER(PARTITION BY queue_type, team_type ORDER BY rating DESC) as global_rank, "
            + "RANK() OVER(PARTITION BY queue_type, team_type, region ORDER BY rating DESC) as region_rank, "
            + "RANK() OVER(PARTITION BY queue_type, team_type, league_type ORDER BY rating DESC) as league_rank "
            + "FROM team "
            + "WHERE season = :season "
            + "AND id NOT IN(SELECT team_id FROM cheaters)"
        + ") "
        + "UPDATE team "
        + "set global_rank = ranks.global_rank, "
        + "region_rank = ranks.region_rank, "
        + "league_rank = ranks.league_rank "
        + "FROM ranks "
        + "WHERE team.id = ranks.id";

    private static final Map<Race, String> FIND_1V1_TEAM_BY_FAVOURITE_RACE_QUERIES = new EnumMap<>(Race.class);

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

    public BigInteger legacyIdOf(BlizzardPlayerCharacter[] characters, Race... races)
    {
        StringBuilder sb = new StringBuilder();
        Arrays.sort(characters, Comparator
                .comparingInt(BlizzardPlayerCharacter::getRealm)
                .thenComparingLong(BlizzardPlayerCharacter::getId));
        Arrays.sort(races);
        for(BlizzardPlayerCharacter c : characters) sb.append(c.getRealm()).append(c.getId());
        for(Race r : races) sb.append(conversionService.convert(r, Integer.class));
        return new BigInteger(sb.toString());
    }

    public BigInteger legacyIdOf(BaseLeague league, BlizzardTeam bTeam)
    {
        BlizzardPlayerCharacter[] bChars = Arrays.stream(bTeam.getMembers())
            .map(BlizzardTeamMember::getCharacter)
            .toArray(BlizzardPlayerCharacter[]::new);
        return legacyIdOf(bChars, extractLegacyIdRaces(league, bTeam));
    }

    public BigInteger legacyIdOf(BaseLeague league, BlizzardProfileTeam bTeam)
    {
        return legacyIdOf
        (
            bTeam.getTeamMembers(),
            league.getQueueType() == QueueType.LOTV_1V1
                ? new Race[]{bTeam.getTeamMembers()[0].getFavoriteRace()}
                : Race.EMPTY_RACE_ARRAY
        );
    }

    private static Race[] extractLegacyIdRaces(BaseLeague league, BlizzardTeam bTeam)
    {
        Race[] races;
        if(league.getQueueType() == QueueType.LOTV_1V1)
        {
            BaseLocalTeamMember member = new BaseLocalTeamMember();
            for(BlizzardTeamMemberRace race : bTeam.getMembers()[0].getRaces())
                member.setGamesPlayed(race.getRace(), race.getGamesPlayed());
            races = new Race[]{member.getFavoriteRace()};
        }
        else
        {
            races = Race.EMPTY_RACE_ARRAY;
        }
        return races;
    }

    private static void initMappers(ConversionService conversionService)
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, i)->
        {
            Team team = new Team
            (
                rs.getLong("team.id"),
                rs.getInt("team.season"),
                conversionService.convert(rs.getInt("team.region"), Region.class),
                new BaseLeague
                    (
                        conversionService.convert(rs.getInt("team.league_type"), League.LeagueType.class),
                        conversionService.convert(rs.getInt("team.queue_type"), QueueType.class),
                        conversionService.convert(rs.getInt("team.team_type"), TeamType.class)
                    ),
                conversionService.convert(DAOUtils.getInteger(rs, "team.tier_type"), LeagueTier.LeagueTierType.class),
                ((BigDecimal) rs.getObject("team.legacy_id")).toBigInteger(),
                rs.getInt("team.division_id"),
                rs.getLong("team.rating"),
                rs.getInt("team.wins"), rs.getInt("team.losses"), rs.getInt("team.ties"),
                rs.getInt("team.points")
            );
            team.setGlobalRank(DAOUtils.getInteger(rs, "team.global_rank"));
            team.setRegionRank(DAOUtils.getInteger(rs, "team.region_rank"));
            team.setLeagueRank(DAOUtils.getInteger(rs, "team.league_rank"));
            return team;
        };
        if(STD_EXTRACTOR == null) STD_EXTRACTOR = DAOUtils.getResultSetExtractor(STD_ROW_MAPPER);
    }

    private static void initQueries(ConversionService conversionService)
    {
        if(FIND_1V1_TEAM_BY_FAVOURITE_RACE_QUERIES.isEmpty())
        {
            String template =
                "SELECT " + TeamDAO.STD_SELECT + ", " + TeamMemberDAO.STD_SELECT
                    + "FROM team_member "
                    + "INNER JOIN team ON team_member.team_id = team.id "
                    + "WHERE team_member.player_character_id = :playerCharacterId "
                    + "AND team_member.%1$s_games_played > 0 "
                    + "AND team.season = :season "
                    + "AND team.region = :region "
                    + "AND team.queue_type = " + conversionService.convert(QueueType.LOTV_1V1, Integer.class) + " "
                    + "AND team.team_type = " + conversionService.convert(TeamType.ARRANGED, Integer.class);
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

    public Team[] merge(Team... teams)
    {
        if(teams.length == 0) return new Team[0];

        List<Object[]> data = Arrays.stream(teams)
            .map(t->new Object[]{
                t.getLegacyId(),
                t.getDivisionId(),
                t.getSeason(),
                conversionService.convert(t.getRegion(), Integer.class),
                conversionService.convert(t.getLeagueType(), Integer.class),
                conversionService.convert(t.getQueueType(), Integer.class),
                conversionService.convert(t.getTeamType(), Integer.class),
                t.getRating(),
                t.getPoints(),
                t.getWins(),
                t.getLosses(),
                t.getTies(),
                conversionService.convert(t.getTierType(), Integer.class),
            })
            .collect(Collectors.toList());
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("teams", data);
        List<Team> mergedTeams = template.query(MERGE_BY_FAVORITE_RACE_QUERY, params, STD_ROW_MAPPER);

        return Arrays.stream(DAOUtils.updateOriginals(teams, mergedTeams, (o, m)->o.setId(m.getId()), o->o.setId(null)))
            .filter(t->t.getId() != null)
            .toArray(Team[]::new);
    }

    public Optional<Team> findById(long id)
    {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id);
        return Optional.ofNullable(template.query(FIND_BY_ID_QUERY, params, getStdExtractor()));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void updateRanks(int season)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("season", season)
            .addValue("seasons", season)
            .addValue
            (
                "cheaterReportType",
                conversionService.convert(PlayerCharacterReport.PlayerCharacterReportType.CHEATER, Integer.class)
            );
        template.update(CALCULATE_RANK_QUERY, params);
        LOG.debug("Calculated team ranks for {} season", season);
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

    public List<Long> findCheaterTeamIds(int season)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("seasons", season)
            .addValue
            (
                "cheaterReportType",
                conversionService.convert(PlayerCharacterReport.PlayerCharacterReportType.CHEATER, Integer.class)
            );
        return template.query(FIND_CHEATER_TEAM_IDS_BY_SEASON_QUERY, params, DAOUtils.LONG_MAPPER);
    }

    private MapSqlParameterSource createParameterSource(Team team)
    {
        return new MapSqlParameterSource()
            .addValue("legacyId", team.getLegacyId())
            .addValue("divisionId", team.getDivisionId())
            .addValue("season", team.getSeason())
            .addValue("region", conversionService.convert(team.getRegion(), Integer.class))
            .addValue("leagueType", conversionService.convert(team.getLeagueType(), Integer.class))
            .addValue("queueType", conversionService.convert(team.getQueueType(), Integer.class))
            .addValue("teamType", conversionService.convert(team.getTeamType(), Integer.class))
            .addValue("tierType", conversionService.convert(team.getTierType(), Integer.class))
            .addValue("rating", team.getRating())
            .addValue("points", team.getPoints())
            .addValue("wins", team.getWins())
            .addValue("losses", team.getLosses())
            .addValue("ties", team.getTies());
    }

}
