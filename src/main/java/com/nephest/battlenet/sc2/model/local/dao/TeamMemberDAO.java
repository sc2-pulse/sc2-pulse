// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.TeamMember;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TeamMemberDAO
{
    private static final String CREATE_QUERY = "INSERT INTO team_member "
        + "(team_id, player_character_id, terran_games_played, protoss_games_played, zerg_games_played, random_games_played) "
        + "VALUES (:teamId, :playerCharacterId, :terranGamesPlayed, :protossGamesPlayed, :zergGamesPlayed, :randomGamesPlayed)";

    private static final String MERGE_QUERY = CREATE_QUERY
        + " "
        + "ON CONFLICT(team_id, player_character_id) DO UPDATE SET "
        + "terran_games_played=excluded.terran_games_played, "
        + "protoss_games_played=excluded.protoss_games_played, "
        + "zerg_games_played=excluded.zerg_games_played, "
        + "random_games_played=excluded.random_games_played";

    private NamedParameterJdbcTemplate template;

    @Autowired
    public TeamMemberDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template
    )
    {
        this.template = template;
    }

    public TeamMember create(TeamMember member)
    {
        MapSqlParameterSource params = createParameterSource(member);
        template.update(CREATE_QUERY, params);
        return member;
    }

    public TeamMember merge(TeamMember member)
    {
        MapSqlParameterSource params = createParameterSource(member);
        template.update(MERGE_QUERY, params);
        return member;
    }

    private MapSqlParameterSource createParameterSource(TeamMember member)
    {
        return new MapSqlParameterSource()
            .addValue("teamId", member.getTeamId())
            .addValue("playerCharacterId", member.getCharacterId())
            .addValue("terranGamesPlayed", member.getTerranGamesPlayed())
            .addValue("protossGamesPlayed", member.getProtossGamesPlayed())
            .addValue("zergGamesPlayed", member.getZergGamesPlayed())
            .addValue("randomGamesPlayed", member.getRandomGamesPlayed());
    }

}
