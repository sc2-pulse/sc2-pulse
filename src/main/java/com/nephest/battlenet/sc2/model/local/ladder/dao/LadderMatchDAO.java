// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.model.local.MatchParticipant;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchParticipantDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMatch;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMatchParticipant;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class LadderMatchDAO
{

    private static final String FIND_MATCHES_BY_CHARACTER_ID =
        "WITH match_filter AS "
        + "("
            + "SELECT DISTINCT(match.id) "
            + "FROM match "
            + "INNER JOIN match_participant ON match.id = match_participant.match_id "
            + "INNER JOIN player_character ON match_participant.player_character_id = player_character.id "
            + "WHERE player_character.id = :playerCharacterId"
        + ")"
        + "SELECT "
        + MatchDAO.STD_SELECT + ", "
        + MatchParticipantDAO.STD_SELECT + ", "
        + AccountDAO.STD_SELECT + ", "
        + PlayerCharacterDAO.STD_SELECT + ", "
        + "pro_player.nickname AS \"pro_player.nickname\", "
        + "COALESCE(pro_team.short_name, pro_team.name) AS \"pro_player.team\" "

        + "FROM match_filter "
        + "INNER JOIN match ON match_filter.id = match.id "
        + "INNER JOIN match_participant ON match.id = match_participant.match_id "
        + "INNER JOIN player_character ON match_participant.player_character_id = player_character.id "
        + "INNER JOIN account ON player_character.account_id = account.id "
        + "LEFT JOIN pro_player_account ON account.id=pro_player_account.account_id "
        + "LEFT JOIN pro_player ON pro_player_account.pro_player_id=pro_player.id "
        + "LEFT JOIN pro_team_member ON pro_player.id=pro_team_member.pro_player_id "
        + "LEFT JOIN pro_team ON pro_team_member.pro_team_id=pro_team.id "
        + "ORDER BY match.date DESC, match.type DESC, match.map DESC, player_character.id DESC";

    private static ResultSetExtractor<List<LadderMatch>> MATCHES_EXTRACTOR;

    private final NamedParameterJdbcTemplate template;

    @Autowired
    public LadderMatchDAO(@Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template)
    {
        this.template = template;
        initMappers();
    }

    private void initMappers()
    {
        if (MATCHES_EXTRACTOR == null) MATCHES_EXTRACTOR = (rs)->
        {
            List<LadderMatch> matches = new ArrayList<>();
            List<LadderMatchParticipant> participants = null;
            long prevMatchId = -1;
            while(rs.next())
            {
                long matchId = rs.getLong("match.id");
                if(matchId != prevMatchId)
                {
                    participants = new ArrayList<>();
                    matches.add(new LadderMatch
                    (
                        MatchDAO.getStdRowMapper().mapRow(rs, 0),
                        participants
                    ));
                    prevMatchId = matchId;
                }

                LadderTeamMember member = new LadderTeamMember
                (
                    AccountDAO.getStdRowMapper().mapRow(rs, 0),
                    PlayerCharacterDAO.getStdRowMapper().mapRow(rs, 0),
                    rs.getString("pro_player.nickname"),
                    rs.getString("pro_player.team"),
                    null, null, null, null
                );
                MatchParticipant participant = MatchParticipantDAO.getStdRowMapper().mapRow(rs, 0);
                participants.add(new LadderMatchParticipant(participant, member));
            }
            return matches;
        };
    }

    public List<LadderMatch> findMatchesByCharacterId(long characterId)
    {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("playerCharacterId", characterId);
        return template.query(FIND_MATCHES_BY_CHARACTER_ID, params, MATCHES_EXTRACTOR);
    }

}
