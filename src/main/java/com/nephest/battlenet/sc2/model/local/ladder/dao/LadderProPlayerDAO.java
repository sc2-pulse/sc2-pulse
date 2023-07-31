// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.ProTeam;
import com.nephest.battlenet.sc2.model.local.ProTeamMember;
import com.nephest.battlenet.sc2.model.local.SocialMediaLink;
import com.nephest.battlenet.sc2.model.local.dao.DAOUtils;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerDAO;
import com.nephest.battlenet.sc2.model.local.dao.ProTeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.ProTeamMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.SocialMediaLinkDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderProPlayer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LadderProPlayerDAO
{

    private static final String FIND_PRO_PLAYER_ID_BY_CHARACTER_ID =
        "SELECT pro_player_id "
        + "FROM player_character "
        + "INNER JOIN account ON player_character.account_id = account.id "
        + "INNER JOIN pro_player_account ON account.id = pro_player_account.account_id "
        + "WHERE player_character.id IN(:playerCharacterIds)";
    private static final String FIND_PRO_PLAYER_BY_BATTLE_TAG =
        "SELECT pro_player_id "
        + "FROM account "
        + "INNER JOIN pro_player_account ON account.id = pro_player_account.account_id "
        + "WHERE account.battle_tag IN(:battletags)";

    private final ProPlayerDAO proPlayerDAO;
    private final ProTeamDAO proTeamDAO;
    private final ProTeamMemberDAO proTeamMemberDAO;
    private final SocialMediaLinkDAO socialMediaLinkDAO;

    private final NamedParameterJdbcTemplate template;

    @Autowired
    public LadderProPlayerDAO
    (
        ProPlayerDAO proPlayerDAO,
        ProTeamDAO proTeamDAO,
        ProTeamMemberDAO proTeamMemberDAO,
        SocialMediaLinkDAO socialMediaLinkDAO,
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template
    )
    {
        this.proPlayerDAO = proPlayerDAO;
        this.proTeamDAO = proTeamDAO;
        this.proTeamMemberDAO = proTeamMemberDAO;
        this.socialMediaLinkDAO = socialMediaLinkDAO;
        this.template = template;
    }

    public List<LadderProPlayer> findByIds(Long... ids)
    {
        List<ProPlayer> players = proPlayerDAO.find(ids);
        Map<Long, Long> teamMembers = proTeamMemberDAO.findByProPlayerIds(ids).stream()
            .collect(Collectors.toMap(ProTeamMember::getProPlayerId, ProTeamMember::getProTeamId));
        Long[] teamIds = teamMembers.values().toArray(Long[]::new);
        Map<Long, ProTeam> teams = proTeamDAO.find(teamIds).stream()
            .collect(Collectors.toMap(ProTeam::getId, Function.identity()));
        Map<Long, List<SocialMediaLink>> links = socialMediaLinkDAO.find(ids).stream()
            .collect(Collectors.groupingBy(SocialMediaLink::getProPlayerId));
        return players.stream()
            .map(player -> new LadderProPlayer(
                player,
                teams.get(teamMembers.get(player.getId())),
                links.get(player.getId())
            ))
            .collect(Collectors.toList());
    }

    public List<LadderProPlayer> findByCharacterIds(Long... ids)
    {
        MapSqlParameterSource params = new MapSqlParameterSource("playerCharacterIds", Set.of(ids));
        Long[] proPlayerIds = template.query
        (
            FIND_PRO_PLAYER_ID_BY_CHARACTER_ID,
            params,
            DAOUtils.LONG_MAPPER
        ).toArray(Long[]::new);
        return findByIds(proPlayerIds);
    }

    public List<LadderProPlayer> findByBattletags(String... btags)
    {
        MapSqlParameterSource params = new MapSqlParameterSource("battletags", Set.of(btags));
        Long[] proPlayerIds = template.query
        (
            FIND_PRO_PLAYER_BY_BATTLE_TAG,
            params,
            DAOUtils.LONG_MAPPER
        ).toArray(Long[]::new);
        return findByIds(proPlayerIds);
    }

}
