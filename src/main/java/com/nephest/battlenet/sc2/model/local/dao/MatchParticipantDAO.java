// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.MatchParticipant;
import com.nephest.battlenet.sc2.web.service.BlizzardSC2API;
import com.nephest.battlenet.sc2.web.service.StatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class MatchParticipantDAO
{
    
    public static final int IDENTIFICATION_FRAME_MINUTES = 25;

    public static final String STD_SELECT =
        "match_participant.match_id AS \"match_participant.match_id\", "
        + "match_participant.player_character_id AS \"match_participant.player_character_id\", "
        + "match_participant.team_id AS \"match_participant.team_id\", "
        + "match_participant.team_state_timestamp AS \"match_participant.team_state_timestamp\", "
        + "match_participant.decision AS \"match_participant.decision\" ";
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
            // API data can have multiple participants for the same match. Ignore such conflicts.
            + "SELECT DISTINCT ON(match_id, player_character_id) * "
            + "FROM missing "
            + "ORDER BY match_id, player_character_id, decision "
            + "RETURNING 1"
        + ") "
        + "SELECT COUNT(*) FROM updated, inserted";

    private static final String IDENTIFY_MATCH_FILTER_TEMPLATE =
        "match_filter AS "
        + "("
            + "SELECT match.id "
            + "FROM match "
            + "INNER JOIN match_participant ON match.id = match_participant.match_id "
            + "WHERE match.type = %1$s "
            + "AND updated  >= :point "
            + "GROUP BY match.id "
            + "HAVING COUNT(*) = %2$s AND SUM(decision) = %3$s "
        + ") ";

    private static final String IDENTIFY_JOIN_WHERE =
        "INNER JOIN match USING(id) "
        + "INNER JOIN match_participant AS match_participant_a ON match.id = match_participant_a.match_id "
        + "INNER JOIN team_member USING(player_character_id) "
        + "INNER JOIN team ON team_member.team_id = team.id "
        + "INNER JOIN " 
        + "("
            + "SELECT DISTINCT ON(team_state.team_id, team_state.timestamp) team_state.team_id, team_state.timestamp "
            + "FROM team_state "
            + "ORDER BY team_state.team_id DESC, team_state.timestamp DESC "
        + ") "
        + "team_state ON team.id = team_state.team_id "
        + "AND team_state.timestamp >= match.date - INTERVAL '" + IDENTIFICATION_FRAME_MINUTES + " minutes' "
        + "AND team_state.timestamp <= match.date + INTERVAL '" + IDENTIFICATION_FRAME_MINUTES + " minutes' "
        + "WHERE team.season = :season "
        + "AND team.queue_type = %4$s "
        + "AND team.team_type = %5$s ";
    
    private static final String IDENTIFY_SOLO_PARTICIPANTS_TEMPLATE =
        "WITH "
        + IDENTIFY_MATCH_FILTER_TEMPLATE
        + "UPDATE match_participant "
        + "SET team_id = team.id, "
        + "team_state_timestamp = team_state.timestamp "
        + "FROM match_filter "
        + IDENTIFY_JOIN_WHERE
        + "AND match_participant_a.match_id = match_participant.match_id "
        + "AND match_participant_a.player_character_id = match_participant.player_character_id";
    
    private static final String IDENTIFY_TEAM_PARTICIPANTS_TEMPLATE = 
        "WITH "
        + IDENTIFY_MATCH_FILTER_TEMPLATE
        + ", team_filter AS "
        + "( "
            + "SELECT match.id, "
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
        + "SET team_id = team.id, "
        + "team_state_timestamp = team_state.timestamp "
        + "FROM team_filter "
        + IDENTIFY_JOIN_WHERE
        + "AND team.legacy_id = team_filter.legacy_id "
        + "AND match_participant_a.match_id = match_participant.match_id "
        + "AND match_participant_a.player_character_id = match_participant.player_character_id";

    private static final List<String> IDENTIFY_PARTICIPANT_QUERIES = new ArrayList<>();

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
            long teamId = rs.getLong("match_participant.team_id");
            if(!rs.wasNull()) participant.setTeamId(teamId);
            participant.setTeamStateDateTime(rs.getObject("match_participant.team_state_timestamp", OffsetDateTime.class));
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
                    queueType.getTeamFormat().getMemberCount() * 2,
                    queueType.getTeamFormat().getMemberCount() * winLossSum,
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

    public void merge(MatchParticipant... participants)
    {
        if(participants.length == 0) return;

        List<Object[]> participantsData = Arrays.stream(participants)
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

}
