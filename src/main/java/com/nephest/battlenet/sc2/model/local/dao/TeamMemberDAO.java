// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.TeamMember;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TeamMemberDAO
{
    
    public static final String STD_SELECT = "team_member.team_id AS \"team_member.team_id\", "
        + "team_member.player_character_id AS \"team_member.player_character_id\", "
        + "team_member.terran_games_played AS \"team_member.terran_games_played\", "
        + "team_member.protoss_games_played AS \"team_member.protoss_games_played\", "
        + "team_member.zerg_games_played AS \"team_member.zerg_games_played\", "
        + "team_member.random_games_played AS \"team_member.random_games_played\" ";
    
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

    private final NamedParameterJdbcTemplate template;

    public static final RowMapper<TeamMember> STD_ROW_MAPPER = (rs, i)-> new TeamMember
    (
        rs.getLong("team_member.team_id"),
        rs.getLong("team_member.player_character_id"),
        rs.getInt("team_member.terran_games_played"),
        rs.getInt("team_member.protoss_games_played"),
        rs.getInt("team_member.zerg_games_played"),
        rs.getInt("team_member.random_games_played")
    );

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

    public int[] merge(TeamMember... members)
    {
        if(members.length == 0) return DAOUtils.EMPTY_INT_ARRAY;

        MapSqlParameterSource[] params = new MapSqlParameterSource[members.length];
        for(int i = 0; i < members.length; i++)
        {
            params[i] = createParameterSource(members[i]);
        }

        return template.batchUpdate(MERGE_QUERY, params);
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
