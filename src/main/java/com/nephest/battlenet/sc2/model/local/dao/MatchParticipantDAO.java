// Copyright (C) 2020 Oleksandr Masniuk and contributors
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

@Repository
public class MatchParticipantDAO
{

    public static final String STD_SELECT =
        "match_participant.match_id AS \"match_participant.match_id\", "
        + "match_participant.player_character_id AS \"match_participant.player_character_id\", "
        + "match_participant.decision AS \"match_participant.decision\" ";
    private static final String CREATE_QUERY =
        "INSERT INTO match_participant(match_id, player_character_id, decision)"
            + " VALUES(:matchId, :playerCharacterId, :decision)";
    private static final String MERGE_QUERY = CREATE_QUERY + " "
        + "ON CONFLICT(match_id, player_character_id) DO UPDATE SET "
        + "decision=excluded.decision";

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

    public MatchParticipant merge(MatchParticipant participant)
    {
        MapSqlParameterSource params = createParameterSource(participant);
        template.update(MERGE_QUERY, params);
        return participant;
    }

}
