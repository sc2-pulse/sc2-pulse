// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Match;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MatchDAO
extends StandardDAO
{

    public static final int UPDATED_TTL_DAYS = 30;
    public static final int TTL_DAYS = 90;
    public static final int DURATION_MAX = 5400;
    public static final int DURATION_OFFSET =
        15 //search
        + 5 //ready + countdown
        + 10 //loading
        + 3 //countdown
        + 5 //loading end screen
        + 5 //looking at stats
        ;
    public static final List<BaseMatch.MatchType> DEFAULT_DURATION_MATCH_TYPES = List.of
    (
        BaseMatch.MatchType._1V1
    );

    public static final String STD_SELECT =
        "match.id AS \"match.id\", "
        + "match.date AS \"match.date\", "
        + "match.type AS \"match.type\", "
        + "match.map_id AS \"match.map_id\", "
        + "match.region AS \"match.region\", "
        + "match.updated AS \"match.updated\", "
        + "match.duration AS \"match.duration\" ";
    private static final String MERGE_QUERY =
        "WITH "
        + "vals AS (VALUES :matchUids), "
        + "updated AS "
        + "("
            + "UPDATE match "
            + "SET updated = NOW() "
            + "FROM vals v (date, type, map_id, region)"
            + "WHERE match.date = v.date "
            + "AND match.type = v.type "
            + "AND match.map_id = v.map_id "
            + "AND match.region = v.region "
            + "RETURNING " + STD_SELECT
        + "), "
        + "missing AS "
        + "("
            + "SELECT v.date, v.type, v.map_id, v.region "
            + "FROM vals v (date, type, map_id, region) "
            + "LEFT JOIN updated ON v.date = updated.\"match.date\"  "
            + "AND v.type = updated.\"match.type\" "
            + "AND v.map_id = updated.\"match.map_id\" "
            + "AND v.region = updated.\"match.region\" "
            + "WHERE updated.\"match.id\" IS NULL "
        + "), "
        + "inserted AS "
        + "("
            + "INSERT INTO match (date, type, map_id, region) "
            + "SELECT * FROM missing "
            + "ON CONFLICT(date, type, map_id, region) DO UPDATE "
            + "SET updated = NOW() "
            + "RETURNING " + STD_SELECT
        + ") "
        + "SELECT * FROM updated "
        + "UNION "
        + "SELECT * FROM inserted";
    
    private static final String CREATE_DURATION_DATASET_QUERY =
        "CREATE TEMPORARY TABLE tmp_match_participant_date "
        + "( "
            + "player_character_id BIGINT NOT NULL, "
            + "date TIMESTAMP WITH TIME ZONE NOT NULL "
        + ") ON COMMIT DROP; "
        + "CREATE INDEX ix_tmp_match_participant_date_player_character_id_date ON tmp_match_participant_date(player_character_id, date); "

        + "WITH "
        + "character_filter AS "
        + "("
            + "SELECT DISTINCT(player_character_id) "
            + "FROM match "
            + "INNER JOIN match_participant ON match.id = match_participant.match_id "
            + "WHERE match.date >= :fromHistory "
            + "AND type IN (:types) "
        + ") "
        + "INSERT INTO tmp_match_participant_date(player_character_id, date) "
        + "SELECT player_character_id, date "
        + "FROM match "
        + "INNER JOIN match_participant ON match.id = match_participant.match_id "
        + "INNER JOIN character_filter USING(player_character_id) "
        + "WHERE match.date >= :fromHistory;";

    private static final String UPDATE_DURATION_QUERY =
        "WITH "
        + "match_duration AS "
        + "( "
            + "SELECT id, EXTRACT(EPOCH FROM (match.date - MAX(prev_match.date))) - " + DURATION_OFFSET + " AS duration "
            + "FROM match "
            + "INNER JOIN match_participant ON match.id = match_participant.match_id "
            + "JOIN LATERAL "
            + "( "
                + "SELECT tmp_match_participant_date.date "
                + "FROM tmp_match_participant_date "
                + "WHERE tmp_match_participant_date.player_character_id = match_participant.player_character_id "
                + "AND tmp_match_participant_date.date >= match.date - INTERVAL '" + DURATION_MAX + " seconds' "
                + "AND tmp_match_participant_date.date < match.date "
                + "ORDER BY tmp_match_participant_date.date DESC "
                + "LIMIT 1 "
            + ") prev_match ON true "
            + "WHERE match.date >= :from "
            + "AND match.type IN(:types) "
            + "GROUP BY match.id "
        + ") "
        + "UPDATE match "
        + "SET duration = match_duration.duration "
        + "FROM match_duration "
        + "WHERE match.id = match_duration.id "
        + "AND match_duration.duration > 0 "
        + "AND (match.duration IS NULL OR match.duration > match_duration.duration)";

    private static final String REMOVE_EXPIRED_QUERY = "DELETE FROM match WHERE date < :toDate OR updated < :toUpdated";

    public static final String PRO_MATCH_FILTER_TEMPLATE =
        "WITH pro_filter AS "
        + "( "
            + "SELECT DISTINCT(player_character.id) "
            + "FROM player_character "
            + "INNER JOIN account ON player_character.account_id = account.id "
            + "INNER JOIN pro_player_account ON account.id = pro_player_account.account_id "
            + "INNER JOIN pro_player ON pro_player_account.pro_player_id = pro_player.id "
        + "), "
        + "pro_match_filter AS "
        + "( "
            + "SELECT DISTINCT(match.id) "
            + "FROM pro_filter "
            + "INNER JOIN match_participant ON pro_filter.id = match_participant.player_character_id "
            + "INNER JOIN match ON match_participant.match_id = match.id "
            + "%1$s"
        + ") ";

    private static final String UPDATE_TWITCH_VOD_STATS =
        "WITH pro_match_filter AS "
        + "( "
            + "SELECT DISTINCT(match_id) "
            + "FROM match_participant "
            + "INNER JOIN match ON match_participant.match_id = match.id "
            + "WHERE twitch_video_id IS NOT NULL "
            + "AND match.date > :from "
        + "), "
        + "match_filter AS "
        + "( "
            + "SELECT "
            + "match_participant.match_id AS id, "
            + "MIN(substring(team.legacy_id::text from char_length(team.legacy_id::text))::smallint)::text "
            + "|| "
            + "MAX(substring(team.legacy_id::text from char_length(team.legacy_id::text))::smallint)::text "
            + "AS race, "
            + "MAX(team_state.rating) AS rating_max, "
            + "MIN(team_state.rating) AS rating_min, "
            + "bool_and(twitch_user.sub_only_vod) AS sub_only_vod "
            + "FROM pro_match_filter "
            + "INNER JOIN match_participant USING(match_id) "
            + "INNER JOIN team ON match_participant.team_id = team.id "
            + "INNER JOIN team_state ON match_participant.team_id = team_state.team_id "
            + "AND match_participant.team_state_timestamp = team_state.timestamp "
            + "LEFT JOIN twitch_video ON match_participant.twitch_video_id = twitch_video.id "
            + "LEFT JOIN twitch_user ON twitch_video.twitch_user_id = twitch_user.id "
            + "GROUP BY match_participant.match_id "
            + "HAVING COUNT(*) = 2 "
        + "), "
        + "pov_filter AS " 
        + "( "
            + "SELECT match_participant.match_id AS id, "
            + "string_agg(substring(team.legacy_id::text from char_length(team.legacy_id::text)), '') AS race_vod "
            + "FROM match_filter "
            + "INNER JOIN match_participant ON match_filter.id = match_participant.match_id "
            + "INNER JOIN team ON match_participant.team_id = team.id "
            + "WHERE match_participant.twitch_video_id IS NOT NULL "
            + "GROUP BY match_participant.match_id " 
        + ") "
        + "UPDATE match "
        + "SET vod = true, "
        + "race_vod = pov_filter.race_vod, "
        + "race = match_filter.race, "
        + "rating_max = match_filter.rating_max, "
        + "rating_min = match_filter.rating_min, "
        + "sub_only_vod = match_filter.sub_only_vod "
        + "FROM match_filter "
        + "INNER JOIN pov_filter USING(id) "
        + "WHERE match.id = match_filter.id";

    private static RowMapper<Match> STD_ROW_MAPPER;
    private final ConversionService conversionService;

    @Autowired
    public MatchDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        super(template, "match", "30 DAYS");
        this.conversionService = conversionService;
        initMappers(conversionService);
    }

    @Override
    public int removeExpired()
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("toDate", OffsetDateTime.now().minusDays(TTL_DAYS))
            .addValue("toUpdated", OffsetDateTime.now().minusDays(UPDATED_TTL_DAYS));
        return getTemplate().update(REMOVE_EXPIRED_QUERY, params);
    }

    private static void initMappers(ConversionService conversionService)
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, i)->
        {
            Match match = new Match
            (
                rs.getLong("match.id"),
                rs.getObject("match.date", OffsetDateTime.class),
                conversionService.convert(rs.getInt("match.type"), BaseMatch.MatchType.class),
                rs.getInt("match.map_id"),
                conversionService.convert(rs.getInt("match.region"), Region.class),
                DAOUtils.getInteger(rs, "match.duration")
            );
            match.setUpdated(rs.getObject("match.updated", OffsetDateTime.class));
            return match;
        };
    }

    public static RowMapper<Match> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    private MapSqlParameterSource createParameterSource(Match match)
    {
        return new MapSqlParameterSource()
            .addValue("date", match.getDate())
            .addValue("type", conversionService.convert(match.getType(), Integer.class))
            .addValue("mapId", match.getMapId())
            .addValue("region", conversionService.convert(match.getRegion(), Integer.class))
            .addValue("updated", match.getUpdated());
    }

    public Set<Match> merge(Set<Match> matches)
    {
        if(matches.isEmpty()) return matches;

        List<Object[]> matchUids = matches.stream()
            .map(match->new Object[]{
                match.getDate(),
                conversionService.convert(match.getType(), Integer.class),
                match.getMapId(),
                conversionService.convert(match.getRegion(), Integer.class)
            })
            .collect(Collectors.toList());
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("matchUids", matchUids);

        List<Match> mergedMatches = getTemplate().query(MERGE_QUERY, params, getStdRowMapper());

        return DAOUtils.updateOriginals(matches, mergedMatches, (o, m)->o.setId(m.getId()));
    }

    @Transactional
    public int updateDuration(OffsetDateTime from, List<BaseMatch.MatchType> types)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("from", from)
            .addValue("fromHistory", from.minusSeconds(DURATION_MAX))
            .addValue("types", types.stream()
                .map(t->conversionService.convert(t, Integer.class))
                .collect(Collectors.toList()));

        getTemplate().update(CREATE_DURATION_DATASET_QUERY, params);
        return getTemplate().update(UPDATE_DURATION_QUERY, params);
    }

    @Transactional
    public int updateDuration(OffsetDateTime from)
    {
        return updateDuration(from, DEFAULT_DURATION_MATCH_TYPES);
    }

    public int updateTwitchVodStats(OffsetDateTime from)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("from", from);
        return getTemplate().update(UPDATE_TWITCH_VOD_STATS, params);
    }

}
