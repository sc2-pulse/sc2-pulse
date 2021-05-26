// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.local.MatchParticipant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class MatchParticipantDAO
{

    public static final String STD_SELECT =
        "match_participant.match_id AS \"match_participant.match_id\", "
        + "match_participant.player_character_id AS \"match_participant.player_character_id\", "
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
            + "SELECT * FROM missing "
            + "RETURNING 1"
        + ") "
        + "SELECT COUNT(*) FROM updated, inserted";

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
    }

    private void initMappers(ConversionService conversionService)
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, num)-> new MatchParticipant
        (
            rs.getLong("match_participant.match_id"),
            rs.getLong("match_participant.player_character_id"),
            conversionService.convert(rs.getInt("match_participant.decision"), BaseMatch.Decision.class)
        );
    }

    public static RowMapper<MatchParticipant> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
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

}
