// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.MatchParticipant;
import com.nephest.battlenet.sc2.web.service.BlizzardSC2API;
import com.nephest.battlenet.sc2.web.service.StatsService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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

@Repository
public class MatchParticipantDAO
{
    
    public static final int IDENTIFICATION_FRAME_MINUTES = 60;
    public static final int TWITCH_VOD_HIGH_MMR = 5500;
    public static final int TWITCH_VOD_HIGH_MMR_OFFSET = 95;
    public static final int TWITCH_VOD_OFFSET = 15;

    public static final String STD_SELECT =
        "match_participant.match_id AS \"match_participant.match_id\", "
        + "match_participant.player_character_id AS \"match_participant.player_character_id\", "
        + "match_participant.team_id AS \"match_participant.team_id\", "
        + "match_participant.team_state_timestamp AS \"match_participant.team_state_timestamp\", "
        + "match_participant.decision AS \"match_participant.decision\", "
        + "match_participant.rating_change AS \"match_participant.rating_change\" ";
    private static final String MERGE_QUERY =
        "WITH "
        + "vals AS (VALUES :participants), "
        + "updated AS "
        + "("
            + "UPDATE match_participant "
            + "SET decision = v.decision "
            + "FROM vals v (match_id, player_character_id, decision) "
            + "WHERE match_participant.match_id = v.match_id "
            + "AND match_participant.player_character_id = v.player_character_id "
            + "AND match_participant.decision != v.decision "
            + "RETURNING 1"
        + "), "
        + "missing AS "
        + "("
            + "SELECT v.match_id, v.player_character_id, v.decision "
            + "FROM vals v (match_id, player_character_id, decision) "
            + "LEFT JOIN match_participant ON v.match_id = match_participant.match_id "
                + "AND v.player_character_id = match_participant.player_character_id "
            + "WHERE match_participant.decision IS NULL"
        + "), "
        + "inserted AS "
        + "("
            + "INSERT INTO match_participant(match_id, player_character_id, decision) "
            + "SELECT * "
            + "FROM missing "
            + "ON CONFLICT(match_id, player_character_id) DO NOTHING "
            + "RETURNING 1"
        + ") "
        + "SELECT COUNT(*) FROM updated, inserted";

    private static final String IDENTIFY_MATCH_FILTER_TEMPLATE =
        "max_ladder_update AS "
        + "("
            + "SELECT region, queue_type, league_type, "
            + "MAKE_INTERVAL(secs=>(MAX(duration) * 2)::double precision) as duration "
            + "FROM ladder_update "
            + "WHERE created >= :point "
            + "and queue_type = %2$s "
            + "GROUP BY region, queue_type, league_type "
        + "), "
        + "match_filter AS "
        + "("
            + "SELECT match.id "
            + "FROM match "
            + "WHERE match.type = %1$s "
            + "AND \"date\"  >= :point "
        + "), "
        + "result_filter AS "
        + "( "
            + "SELECT match_participant.match_id, match_participant.player_character_id, "
            + "team.id AS team_id, team_state.timestamp, match.date, "
            + "ROW_NUMBER() OVER ( "
                + "PARTITION BY match_participant.match_id, match_participant.player_character_id "
                + "ORDER BY team_state.timestamp <-> match.date "
            + ") AS closest_ix "
            + "FROM match_filter "
            + "INNER JOIN match USING(id) "
            + "INNER JOIN match_participant ON match.id = match_participant.match_id "
            + "INNER JOIN team_member USING(player_character_id) "
            + "INNER JOIN team ON team_member.team_id = team.id "
            + "INNER JOIN team_state ON team.id = team_state.team_id "
            /*TODO
                This is an experimental change to improve the identification precision. If this
                works, then GIST operators should be replaced with regular SELECT
                DISTINCT ON, and GIST indexes should be removed.
             */
                + "AND team_state.timestamp >= match.date "
                + "AND team_state.timestamp <= match.date + INTERVAL '" + IDENTIFICATION_FRAME_MINUTES + " minutes' "
            + "INNER JOIN division ON team_state.division_id = division.id "
            + "INNER JOIN league_tier ON division.league_tier_id = league_tier.id "
            + "INNER JOIN league ON league_tier.league_id = league.id "
            + "LEFT JOIN max_ladder_update ON team.region = max_ladder_update.region "
                + "AND team.queue_type = max_ladder_update.queue_type "
                + "AND league.type = max_ladder_update.league_type "
            + "WHERE team.season = :season "
            + "AND team.queue_type = %2$s "
            + "AND team.team_type = %3$s "
            + "AND match_participant.team_id IS NULL "
            + "AND "
            + "( "
                + "max_ladder_update.duration IS NULL "
                + "OR team_state.timestamp - match.date <= max_ladder_update.duration "
            +") "
        + ") ";
    
    private static final String IDENTIFY_SOLO_PARTICIPANTS_TEMPLATE =
        "WITH "
        + IDENTIFY_MATCH_FILTER_TEMPLATE
        + "UPDATE match_participant "
        + "SET team_id = result_filter.team_id, "
        + "team_state_timestamp = result_filter.timestamp "
        + "FROM result_filter "
        + "WHERE match_participant.match_id = result_filter.match_id "
        + "AND match_participant.player_character_id = result_filter.player_character_id "
        + "AND result_filter.closest_ix = 1";
    
    private static final String IDENTIFY_TEAM_PARTICIPANTS_TEMPLATE = 
        "WITH "
        + IDENTIFY_MATCH_FILTER_TEMPLATE
        + ", team_filter AS "
        + "( "
            + "SELECT match.id AS match_id, "
            + "string_agg"
            + "("
                + "player_character.realm::text || player_character.battlenet_id::text, "
                + "'' ORDER BY player_character.realm, player_character.battlenet_id"
            + ")::numeric AS legacy_id "
            + "FROM match_filter "
            + "INNER JOIN match USING(id) "
            + "INNER JOIN match_participant ON match.id = match_participant.match_id "
            + "INNER JOIN player_character ON match_participant.player_character_id = player_character.id "
            + "GROUP BY match.id, match_participant.decision "
        + ") "
        + "UPDATE match_participant "
        + "SET team_id = result_filter.team_id, "
        + "team_state_timestamp = result_filter.timestamp "
        + "FROM result_filter "
        + "INNER JOIN team_filter ON team_filter.match_id = result_filter.match_id "
        + "INNER JOIN team ON result_filter.team_id = team.id "
        + "WHERE match_participant.match_id = result_filter.match_id "
        + "AND match_participant.player_character_id = result_filter.player_character_id "
        + "AND result_filter.closest_ix = 1 "
        + "AND team.legacy_id = team_filter.legacy_id ";

    private static final List<String> IDENTIFY_PARTICIPANT_QUERIES = new ArrayList<>();
    
    private static final String UPDATE_RATING_CHANGE =
        "WITH "
        + "match_participant_filter_first AS "
        + "("
            + "SELECT DISTINCT ON(player_character_id) "
            + "match_participant.player_character_id, "
            + "match.date "
            + "FROM match "
            + "INNER JOIN match_participant ON match.id = match_participant.match_id "
            + "WHERE match.date >= :from "
            + "ORDER BY match_participant.player_character_id, match.date"
        + "), "
        + "match_participant_filter_prev AS "
        + "("
            + "SELECT DISTINCT ON(match_participant.player_character_id) "
            + "match.id "
            + "FROM match_participant_filter_first "
            + "INNER JOIN match_participant USING(player_character_id) "
            + "INNER JOIN match ON match_participant.match_id = match.id "
            + "WHERE match.date < match_participant_filter_first.date "
            + "ORDER BY match_participant.player_character_id DESC, match.date DESC"
        + "), "
        + "match_filter_all AS "
        + "("
            + "SELECT id "
            + "FROM match "
            + "WHERE date >= :from "

            + "UNION "

            + "SELECT DISTINCT(id) FROM match_participant_filter_prev"
        + "), "
        + "rating_diff AS "
        + "( "
            + "SELECT match_participant.match_id, "
            + "match_participant.player_character_id, "
            + "CASE "
                + "WHEN "
                    + "LAG(team_state.rating) "
                    + "OVER "
                    + "("
                        + "PARTITION BY match_participant.player_character_id "
                        + "ORDER BY match.date "
                    + ")"
                    + "IS NOT NULL "
                + "THEN "
                    + "team_state.rating - LAG(team_state.rating) "
                        + "OVER "
                        + "("
                            + "PARTITION BY match_participant.player_character_id, team_state.team_id "
                            + "ORDER BY match.date"
                        + ") "
                + "ELSE NULL "
            + "END AS rating_change "
            + "FROM match_filter_all "
            + "INNER JOIN match USING(id) "
            + "INNER JOIN match_participant ON match.id = match_participant.match_id "
            + "LEFT JOIN team_state ON match_participant.team_id = team_state.team_id "
                + "AND match_participant.team_state_timestamp = team_state.timestamp "
        + ") "
        + "UPDATE match_participant "
        + "SET rating_change = rating_diff.rating_change "
        + "FROM rating_diff "
        + "WHERE match_participant.match_id = rating_diff.match_id "
        + "AND match_participant.player_character_id = rating_diff.player_character_id "
        + "AND match_participant.rating_change IS NULL "
        + "AND "
        + "("
            + "(match_participant.decision = :win AND rating_diff.rating_change > 0)"
            + "OR (match_participant.decision = :loss AND rating_diff.rating_change < 0)"
        + ")";

    private static final String LINK_TWITCH_VIDEO = 
        String.format
        (
            MatchDAO.PRO_MATCH_FILTER_TEMPLATE,
            "WHERE match.date > :from "
            + "AND match.type = :matchType "
            + "AND match.duration IS NOT NULL "
        )
        + "UPDATE match_participant "
        + "SET twitch_video_id = twitch_video.id, "
        + "twitch_video_offset = EXTRACT(EPOCH FROM ("
            + "match.date "
            + "- make_interval"
            + "(secs => "
                + "match.duration::double precision "
                + "- " + TWITCH_VOD_OFFSET
            + ") "
            + "- twitch_video.begin)) "
        + "FROM pro_match_filter " + "INNER JOIN match USING(id) "
        + "INNER JOIN match_participant mp ON match.id = mp.match_id "
        + "INNER JOIN team_state ON mp.team_id = team_state.team_id "
            + "AND mp.team_state_timestamp = team_state.timestamp "
        + "INNER JOIN player_character ON mp.player_character_id = player_character.id "
        + "INNER JOIN account ON player_character.account_id = account.id "
        + "INNER JOIN pro_player_account ON account.id = pro_player_account.account_id "
        + "INNER JOIN pro_player ON pro_player_account.pro_player_id = pro_player.id "
        + "INNER JOIN social_media_link ON pro_player.id = social_media_link.pro_player_id "
            + "AND social_media_link.type = " + SocialMedia.TWITCH.getId() + " "
        + "INNER JOIN twitch_user ON social_media_link.service_user_id::bigint = twitch_user.id "
        + "INNER JOIN twitch_video ON match.date - make_interval(secs => match.duration::double precision) "
        + "BETWEEN twitch_video.begin AND twitch_video.\"end\" "
            + "AND twitch_user.id = twitch_video.twitch_user_id "
        + "WHERE match_participant.match_id = mp.match_id "
        + "AND match_participant.player_character_id = mp.player_character_id";

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    private static RowMapper<MatchParticipant> STD_ROW_MAPPER;

    @Autowired
    public MatchParticipantDAO
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

    private void initMappers(ConversionService conversionService)
    {
        if (STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, num) ->
        {
            MatchParticipant participant = new MatchParticipant(
                rs.getLong("match_participant.match_id"),
                rs.getLong("match_participant.player_character_id"),
                conversionService.convert(rs.getInt("match_participant.decision"), BaseMatch.Decision.class)
            );
            participant.setTeamId(DAOUtils.getLong(rs, "match_participant.team_id"));
            participant.setTeamStateDateTime(rs.getObject("match_participant.team_state_timestamp", OffsetDateTime.class));
            participant.setRatingChange(DAOUtils.getInteger(rs, "match_participant.rating_change"));
           return participant;
        };
    }

    public static RowMapper<MatchParticipant> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    private static void initQueries(ConversionService conversionService)
    {
        int winLossSum = conversionService.convert(BaseMatch.Decision.WIN, Integer.class)
            + conversionService.convert(BaseMatch.Decision.LOSS, Integer.class);
        for(QueueType queueType : QueueType.getTypes(StatsService.VERSION))
        {
            for(TeamType teamType : TeamType.values())
            {
                if(!BlizzardSC2API.isValidCombination(BaseLeague.LeagueType.BRONZE, queueType, teamType)) continue;

                String query = queueType.getTeamFormat().getMemberCount(teamType) == 1
                    ? IDENTIFY_SOLO_PARTICIPANTS_TEMPLATE
                    : IDENTIFY_TEAM_PARTICIPANTS_TEMPLATE;
                IDENTIFY_PARTICIPANT_QUERIES.add(String.format(query,
                    conversionService.convert(BaseMatch.MatchType.from(queueType.getTeamFormat()), Integer.class),
                    conversionService.convert(queueType, Integer.class),
                    conversionService.convert(teamType, Integer.class)
                ));
            }
        }
    }

    private MapSqlParameterSource createParameterSource(MatchParticipant participant)
    {
        return new MapSqlParameterSource()
            .addValue("matchId", participant.getMatchId())
            .addValue("playerCharacterId", participant.getPlayerCharacterId())
            .addValue("decision", conversionService.convert(participant.getDecision(), Integer.class));
    }

    public void merge(Set<MatchParticipant> participants)
    {
        if(participants.isEmpty()) return;

        List<Object[]> participantsData = participants.stream()
            .map(participant->new Object[]{
                participant.getMatchId(),
                participant.getPlayerCharacterId(),
                conversionService.convert(participant.getDecision(), Integer.class)
            })
            .collect(Collectors.toList());
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("participants", participantsData);

        template.query(MERGE_QUERY, params, DAOUtils.INT_EXTRACTOR);
    }

    public int identify(int season, OffsetDateTime from)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("season", season)
            .addValue("point", from);
        int identified = 0;
        for(String q : IDENTIFY_PARTICIPANT_QUERIES) identified += template.update(q, params);
        return identified;
    }

    public int calculateRatingDifference(OffsetDateTime from)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("from", from)
            .addValue("win", conversionService.convert(BaseMatch.Decision.WIN, Integer.class))
            .addValue("loss", conversionService.convert(BaseMatch.Decision.LOSS, Integer.class));
        return template.update(UPDATE_RATING_CHANGE, params);
    }

    public int linkTwitchVideo(OffsetDateTime from)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("from", from)
            .addValue("matchType", conversionService.convert(BaseMatch.MatchType._1V1, Integer.class));
        return template.update(LINK_TWITCH_VIDEO, params);
    }

}
