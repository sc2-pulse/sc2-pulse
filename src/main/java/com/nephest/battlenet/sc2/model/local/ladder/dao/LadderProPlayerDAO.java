// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.ProTeam;
import com.nephest.battlenet.sc2.model.local.SocialMediaLink;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerDAO;
import com.nephest.battlenet.sc2.model.local.dao.ProTeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.SocialMediaLinkDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderProPlayer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class LadderProPlayerDAO
{

    private static final String FIND_PRO_PLAYER_TEMPLATE =
        "SELECT "
        + ProPlayerDAO.STD_SELECT + ", "
        + SocialMediaLinkDAO.STD_SELECT + ", "
        + ProTeamDAO.STD_SELECT + " "

        + "FROM player_character "
        + "INNER JOIN account ON player_character.account_id = account.id "
        + "INNER JOIN pro_player_account ON account.id = pro_player_account.account_id "
        + "INNER JOIN pro_player ON pro_player_account.pro_player_id = pro_player.id "
        + "LEFT JOIN social_media_link ON pro_player.id = social_media_link.pro_player_id "
        + "LEFT JOIN pro_team_member ON pro_player.id=pro_team_member.pro_player_id "
        + "LEFT JOIN pro_team ON pro_team_member.pro_team_id=pro_team.id ";

    private static final String FIND_PRO_PLAYER_BY_CHARACTER_ID_QUERY = FIND_PRO_PLAYER_TEMPLATE
        + "WHERE player_character.id = :playerCharacterId";
    private static final String FIND_PRO_PLAYER_BY_BATTLE_TAG = FIND_PRO_PLAYER_TEMPLATE
        + "WHERE account.battle_tag = :battletag";

    public static final ResultSetExtractor<LadderProPlayer> LADDER_PRO_PLAYER_EXTRACTOR = (rs)->
    {
        ProPlayer proPlayer = null;
        ProTeam proTeam = null;
        List<SocialMediaLink> links = new ArrayList<>();
        while(rs.next())
        {
            if(proPlayer == null) proPlayer = ProPlayerDAO.getStdRowMapper().mapRow(rs, 1);
            if(proTeam == null)
            {
                rs.getLong("pro_team.id");
                if(!rs.wasNull()) proTeam = ProTeamDAO.getStdRowMapper().mapRow(rs,1);
            }
            rs.getLong("social_media_link.pro_player_id");
            if(!rs.wasNull()) links.add(SocialMediaLinkDAO.getStdRowMapper().mapRow(rs, 1));
        }
        return new LadderProPlayer(proPlayer, proTeam, links);
    };

    private final NamedParameterJdbcTemplate template;

    @Autowired
    public LadderProPlayerDAO(NamedParameterJdbcTemplate template)
    {
        this.template = template;
    }

    public LadderProPlayer getProPlayerByCharacterId(long id)
    {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("playerCharacterId", id);
        return template.query(FIND_PRO_PLAYER_BY_CHARACTER_ID_QUERY, params, LADDER_PRO_PLAYER_EXTRACTOR);
    }

    public LadderProPlayer getProPlayerByBattletag(String btag)
    {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("battletag", btag);
        return template.query(FIND_PRO_PLAYER_BY_BATTLE_TAG, params, LADDER_PRO_PLAYER_EXTRACTOR);
    }

}
