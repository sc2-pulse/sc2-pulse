// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.local.MatchParticipant;
import com.nephest.battlenet.sc2.model.local.dao.*;
import com.nephest.battlenet.sc2.model.local.ladder.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class LadderMatchDAO
{

    private static final String FIND_MATCHES_BY_CHARACTER_ID_TEMPLATE =
        "WITH match_filter AS "
        + "("
            + "SELECT DISTINCT(match.id) "
            + "FROM match "
            + "INNER JOIN match_participant ON match.id = match_participant.match_id "
            + "INNER JOIN player_character ON match_participant.player_character_id = player_character.id "
            + "WHERE player_character.id = :playerCharacterId "
        + "), "
        /*
            We can't guarantee anything if there are unidentified members in the match. Exclude such matches.
            Allow the clients to decide what to do with the data, don't force any opinions on the data except validity.
         */
        + "valid_match_filter AS "
        + "( "
            + "SELECT MAX(match_filter.id) AS id "
            + "FROM match_filter "
            + "INNER JOIN match USING(id) "
            + "INNER JOIN match_participant ON match_filter.id = match_participant.match_id "
            + "WHERE (date, type, map) %1$s (:dateAnchor, :typeAnchor, :mapAnchor) "
            + "GROUP BY date, type, map "
            + "ORDER BY (date, type, map) %2$s "
            + "LIMIT :limit"
        + ") "
        + "SELECT "
        + MatchDAO.STD_SELECT + ", "
        + MatchParticipantDAO.STD_SELECT + ", "
        + TeamDAO.STD_SELECT + ", "
        + "team_member.terran_games_played, team_member.protoss_games_played, "
        + "team_member.zerg_games_played, team_member.random_games_played, "
        + TeamStateDAO.STD_SELECT + ", "
        + "league_tier.type AS \"league_tier.type\", "
        + LeagueDAO.STD_SELECT + ", "
        + "season.battlenet_id as \"season.battlenet_id\", "
        + "NULL as \"race\", "
        + AccountDAO.STD_SELECT + ", "
        + PlayerCharacterDAO.STD_SELECT + ", "
        + ClanDAO.STD_SELECT + ", "
        + "pro_player.nickname AS \"pro_player.nickname\", "
        + "COALESCE(pro_team.short_name, pro_team.name) AS \"pro_player.team\" "

        + "FROM valid_match_filter "
        + "INNER JOIN match ON valid_match_filter.id = match.id "
        + "INNER JOIN match_participant ON match.id = match_participant.match_id "
        + "LEFT JOIN team ON match_participant.team_id = team.id "
        + "LEFT JOIN division ON team.division_id = division.id "
        + "LEFT JOIN league_tier ON division.league_tier_id = league_tier.id "
        + "LEFT JOIN league ON league_tier.league_id = league.id "
        + "LEFT JOIN season ON league.season_id = season.id "
        + "LEFT JOIN team_member ON team.id = team_member.team_id "
        + "LEFT JOIN team_state ON match_participant.team_id = team_state.team_id "
            + "AND match_participant.team_state_timestamp = team_state.timestamp "
        + "LEFT JOIN player_character ON team_member.player_character_id = player_character.id "
        + "LEFT JOIN account ON player_character.account_id = account.id "
        + "LEFT JOIN clan_member ON player_character.id = clan_member.player_character_id "
        + "LEFT JOIN clan ON clan_member.clan_id = clan.id "
        + "LEFT JOIN pro_player_account ON account.id=pro_player_account.account_id "
        + "LEFT JOIN pro_player ON pro_player_account.pro_player_id=pro_player.id "
        + "LEFT JOIN pro_team_member ON pro_player.id=pro_team_member.pro_player_id "
        + "LEFT JOIN pro_team ON pro_team_member.pro_team_id=pro_team.id "
        + "ORDER BY (match.date , match.type , match.map) %2$s, "
            + "(match_participant.match_id, match_participant.player_character_id) %2$s ";

    public static String FIND_MATCHES_BY_CHARACTER_ID =
        String.format(FIND_MATCHES_BY_CHARACTER_ID_TEMPLATE, "<", "DESC");
    public static String FIND_MATCHES_BY_CHARACTER_ID_REVERSED =
        String.format(FIND_MATCHES_BY_CHARACTER_ID_TEMPLATE, ">", "ASC");

    private static ResultSetExtractor<LadderMatchParticipant> PARTICIPANT_EXTRACTOR;
    private static ResultSetExtractor<List<LadderMatch>> MATCHES_EXTRACTOR;

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    private int resultsPerPage = 10;

    @Autowired
    public LadderMatchDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        initMappers();
    }

    private void initMappers()
    {
        initParticipantExtractor();
        initMatchesExtractor();
    }

    private void initParticipantExtractor()
    {
        if(PARTICIPANT_EXTRACTOR == null) PARTICIPANT_EXTRACTOR = rs->
        {
            if(rs.isAfterLast()) return null;

            MatchParticipant participant = MatchParticipantDAO.getStdRowMapper().mapRow(rs, 0);
            rs.getLong("team.id");
            if(rs.wasNull()) {
                rs.next();
                return new LadderMatchParticipant(participant, null, null);
            }

            LadderTeamState state = LadderTeamStateDAO.getStdRowMapper().mapRow(rs, 0);
            LadderTeam team = LadderSearchDAO.getLadderTeamMapper().mapRow(rs, 0);
            do
            {
                if(rs.getLong("match_participant.match_id") != participant.getMatchId()
                    || rs.getLong("match_participant.player_character_id") != participant.getPlayerCharacterId())
                    break;
                team.getMembers().add(LadderSearchDAO.getLadderTeamMemberMapper().mapRow(rs, 0));
            }
            while(rs.next());
            return new LadderMatchParticipant(participant, team, state);
        };
    }

    private void initMatchesExtractor()
    {
        if (MATCHES_EXTRACTOR == null) MATCHES_EXTRACTOR = (rs)->
        {
            List<LadderMatch> matches = new ArrayList<>();
            if(!rs.next()) return matches;

            do
            {
                LadderMatch match = new LadderMatch(MatchDAO.getStdRowMapper().mapRow(rs, 0), new ArrayList<>());
                matches.add(match);
                while(!rs.isAfterLast() && rs.getLong("match.id") == match.getMatch().getId())
                    match.getParticipants().add(getParticipantExtractor().extractData(rs));
            }
            while(!rs.isAfterLast());
            return matches;
        };
    }

    public ResultSetExtractor<LadderMatchParticipant> getParticipantExtractor()
    {
        return PARTICIPANT_EXTRACTOR;
    }

    public ResultSetExtractor<List<LadderMatch>> getMatchesExtractor()
    {
        return MATCHES_EXTRACTOR;
    }

    public int getResultsPerPage()
    {
        return resultsPerPage;
    }

    protected void setResultsPerPage(int resultsPerPage)
    {
        this.resultsPerPage = resultsPerPage;
    }

    public PagedSearchResult<List<LadderMatch>> findMatchesByCharacterId
    (long characterId, OffsetDateTime dateAnchor, BaseMatch.MatchType typeAnchor, String mapAnchor, int page, int pageDiff)
    {
        if(Math.abs(pageDiff) != 1) throw new IllegalArgumentException("Invalid page diff");
        boolean forward = pageDiff > -1;
        long finalPage = page + pageDiff;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("playerCharacterId", characterId)
            .addValue("dateAnchor", dateAnchor)
            .addValue("typeAnchor", conversionService.convert(typeAnchor, Integer.class))
            .addValue("mapAnchor", mapAnchor)
            .addValue("limit", getResultsPerPage());
        String q = forward ? FIND_MATCHES_BY_CHARACTER_ID : FIND_MATCHES_BY_CHARACTER_ID_REVERSED;
        List<LadderMatch> matches = template.query(q, params, MATCHES_EXTRACTOR);
        return new PagedSearchResult<>(null, (long) getResultsPerPage(), finalPage, matches);
    }

}
