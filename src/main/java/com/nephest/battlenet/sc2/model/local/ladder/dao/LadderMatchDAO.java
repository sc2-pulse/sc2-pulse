// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.local.MatchParticipant;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterReport;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.DAOUtils;
import com.nephest.battlenet.sc2.model.local.dao.LeagueDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchParticipantDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.SC2MapDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import com.nephest.battlenet.sc2.model.local.inner.VersusSummary;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMatch;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMatchParticipant;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamState;
import com.nephest.battlenet.sc2.model.local.ladder.PagedSearchResult;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LadderMatchDAO
{

    private static final String FIND_MATCHES_TEMPLATE =
        "SELECT "
        + MatchDAO.STD_SELECT + ", "
        + SC2MapDAO.STD_SELECT + ", "
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
        + "COALESCE(pro_team.short_name, pro_team.name) AS \"pro_player.team\", "
        + "confirmed_cheater_report.id AS \"confirmed_cheater_report.id\", "
        + "match_participant.twitch_video_offset AS \"match_participant.twitch_video_offset\", "
        + "twitch_video.url AS \"twitch_video.url\", "
        + "twitch_user.sub_only_vod AS \"twitch_user.sub_only_vod\" "

        + "FROM match_filter "
        + "INNER JOIN match ON match_filter.id = match.id "
        + "INNER JOIN map ON match.map_id = map.id "
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
        + "LEFT JOIN clan ON player_character.clan_id = clan.id "
        + "LEFT JOIN pro_player_account ON account.id=pro_player_account.account_id "
        + "LEFT JOIN pro_player ON pro_player_account.pro_player_id=pro_player.id "
        + "LEFT JOIN pro_team_member ON pro_player.id=pro_team_member.pro_player_id "
        + "LEFT JOIN pro_team ON pro_team_member.pro_team_id=pro_team.id "
        + "LEFT JOIN player_character_report AS confirmed_cheater_report "
            + "ON player_character.id = confirmed_cheater_report.player_character_id "
            + "AND confirmed_cheater_report.type = :cheaterReportType "
            + "AND confirmed_cheater_report.status = true "
        + "LEFT JOIN twitch_video ON match_participant.twitch_video_id = twitch_video.id "
        + "LEFT JOIN twitch_user ON twitch_video.twitch_user_id = twitch_user.id "
        + "ORDER BY (match.date , match.type , match.map_id) %2$s, "
            + "(match_participant.match_id, match_participant.player_character_id) %2$s ";

    private static final String FIND_MATCHES_BY_CHARACTER_ID_TEMPLATE =
        "WITH match_filter AS "
        + "("
            + "SELECT MAX(id) AS id "
            + "FROM match "
            + "INNER JOIN match_participant ON match.id = match_participant.match_id "
            + "WHERE (date, type, map_id) %1$s (:dateAnchor, :typeAnchor, :mapIdAnchor) "
            + "AND (array_length(:types::smallint[], 1) IS NULL OR match.type = ANY(:types)) "
            + "AND match_participant.player_character_id = :playerCharacterId "
            + "GROUP BY date, type, map_id "
            + "ORDER BY (date, type, map_id) %2$s "
            + "LIMIT :limit"
        + ") "
        + FIND_MATCHES_TEMPLATE;

    public static String FIND_MATCHES_BY_CHARACTER_ID =
        String.format(FIND_MATCHES_BY_CHARACTER_ID_TEMPLATE, "<", "DESC");
    public static String FIND_MATCHES_BY_CHARACTER_ID_REVERSED =
        String.format(FIND_MATCHES_BY_CHARACTER_ID_TEMPLATE, ">", "ASC");

    private static final String FIND_TWITCH_VODS =
        String.format
        (
        "WITH match_filter AS "
        + "("
            + "SELECT id "
            + "FROM match "
            + "WHERE vod = true "
            + "AND (date, type, map_id) < (:dateAnchor, :typeAnchor, :mapIdAnchor) "
            + "AND (:mapId::integer IS NULL OR map_id = :mapId) "
            + "AND (:minDuration::integer IS NULL OR duration >= :minDuration) "
            + "AND (:maxDuration::integer IS NULL OR duration <= :maxDuration) "
            + "AND (:minRating::integer IS NULL OR rating_min >= :minRating) "
            + "AND (:maxRating::integer IS NULL OR rating_max <= :maxRating) "
            + "AND (:race::text IS NULL OR race LIKE :race) "
            + "AND (:includeSubOnly = true OR sub_only_vod = false) "
            + "ORDER BY (date, type, map_id) DESC "
            + "LIMIT :limit "
        + ") " + FIND_MATCHES_TEMPLATE,
            "<", "DESC"
        );

    private static final String VERSUS_FILTER_TEMPLATE =
        "WITH "
        + "vs_match_filter AS "
        + "( "
            + "SELECT DISTINCT(id) FROM "
            + "( "
                + "SELECT match.id, player_character_id "
                + "FROM match "
                + "INNER JOIN match_participant ON match.id = match_participant.match_id "
                + "INNER JOIN player_character ON match_participant.player_character_id = player_character.id "
                + "WHERE "
                + "player_character.clan_id = ANY (:clans) "
                + "AND match_participant.decision IN (:validDecisions) "
                + "AND match.type NOT IN (:excludeTypes) "
                + "%1$s "

                + "UNION "

                + "SELECT match.id, player_character_id "
                + "FROM match "
                + "INNER JOIN match_participant ON match.id = match_participant.match_id "
                + "INNER JOIN player_character ON match_participant.player_character_id = player_character.id "
                + "LEFT JOIN team ON match_participant.team_id = team.id "
                + "WHERE "
                + "(team.queue_type, team.region, team.legacy_id) IN (:teams) "
                + "AND match_participant.decision IN (:validDecisions) "
                + "AND match.type NOT IN (:excludeTypes) "
                + "%1$s "
            + ") c "
            + "GROUP BY c.id "
            + "HAVING COUNT(*) > 1 "
        + "),"
        + "match_filter_g1 AS "
        + "("
            + "SELECT id, MAX(decision) AS decision "
            + "FROM "
            + "("
                + "SELECT vs_match_filter.id, MAX(match_participant.decision) AS decision "
                + "FROM vs_match_filter "
                + "INNER JOIN match_participant ON vs_match_filter.id = match_participant.match_id "
                + "INNER JOIN player_character ON match_participant.player_character_id = player_character.id "
                + "WHERE "
                + "player_character.clan_id = ANY (:clans1) "
                + "GROUP BY vs_match_filter.id "
                + "HAVING MAX(match_participant.decision) = MIN(match_participant.decision) "

                + "UNION "

                + "SELECT vs_match_filter.id, MAX(match_participant.decision) AS decision "
                + "FROM vs_match_filter "
                + "INNER JOIN match_participant ON vs_match_filter.id = match_participant.match_id "
                + "LEFT JOIN team ON match_participant.team_id = team.id "
                + "WHERE "
                + "(team.queue_type, team.region, team.legacy_id) IN (:teams1) "
                + "GROUP BY vs_match_filter.id "
                + "HAVING MAX(match_participant.decision) = MIN(match_participant.decision) "
            + ") g1 "
            + "GROUP BY id "
            + "HAVING MAX(decision) = MIN(decision) "
        + "), "
        + "match_filter AS "
        + "( "
            + "SELECT MAX(match.id) AS id, MAX(match_participant.decision) AS decision "
            + "FROM match_filter_g1 "
            + "INNER JOIN match USING (id) "
            + "INNER JOIN match_participant ON match.id = match_participant.match_id "
            + "INNER JOIN player_character ON match_participant.player_character_id = player_character.id "
            + "LEFT JOIN team ON match_participant.team_id = team.id "
            + "WHERE "
            + "("
                + "player_character.clan_id = ANY (:clans2) "
                + "OR (team.queue_type, team.region, team.legacy_id) IN (:teams2) "
            + ") "
            + "GROUP BY date, type, map_id "
            + "HAVING MAX(match_participant.decision) = MIN(match_participant.decision) "
            + "AND MAX(match_participant.decision) != MAX(match_filter_g1.decision) "
            + "ORDER BY (date, type, map_id) %2$s "
            + "%3$s"
        + ") ";

    private static final String VERSUS_SUMMARY =
        String.format
        (
            VERSUS_FILTER_TEMPLATE,
            "AND (array_length(:types::smallint[], 1) IS NULL OR match.type = ANY(:types)) ",
            "DESC", ""
        )
        + ", wins AS (SELECT COUNT(DISTINCT(match_filter.id)) AS wins FROM match_filter WHERE decision = :loss) "
        + "SELECT COUNT(DISTINCT(match_filter.id)) AS matches, "
        + "MAX(wins.wins) AS wins "
        + "FROM match_filter, wins";

    private static final String FIND_VERSUS_MATCHES_TEMPLATE =
        String.format
        (
            VERSUS_FILTER_TEMPLATE,
            "AND (date, type, map_id) %1$s (:dateAnchor, :typeAnchor, :mapIdAnchor) "
            + "AND (array_length(:types::smallint[], 1) IS NULL OR match.type = ANY(:types)) ",
            "%2$s",
            "LIMIT :limit"
        )
        + FIND_MATCHES_TEMPLATE;
    private static final String FIND_VERSUS_MATCHES =
        String.format(FIND_VERSUS_MATCHES_TEMPLATE, "<", "DESC");
    private static final String FIND_VERSUS_MATCHES_REVERSED =
        String.format(FIND_VERSUS_MATCHES_TEMPLATE, ">", "ASC");

    private static List<Integer> DEFAULT_VERSUS_DECISIONS;
    private static List<Integer> DEFAULT_VERSUS_EXCLUDE_TYPES;

    private static final ResultSetExtractor<VersusSummary> VERSUS_SUMMARY_EXTRACTOR = rs->
    {
        if(!rs.next()) return null;

        int matches = rs.getInt("matches");
        int wins = rs.getInt("wins");
        return new VersusSummary(matches, wins, matches - wins);
    };

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
        initMisc();
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
            if(DAOUtils.getLong(rs, "team.id") == null) {
                rs.next();
                return new LadderMatchParticipant(participant, null, null, null, null);
            }

            LadderTeamState state = LadderTeamStateDAO.getStdRowMapper().mapRow(rs, 0);
            LadderTeam team = LadderSearchDAO.getLadderTeamMapper().mapRow(rs, 0);
            String twitchUrl = rs.getString("twitch_video.url");
            Boolean subOnlyVod = null;
            if(twitchUrl != null)
            {
                twitchUrl += "?t=" + rs.getInt("match_participant.twitch_video_offset") + "s";
                subOnlyVod = rs.getBoolean("twitch_user.sub_only_vod");
            }
            do
            {
                if(rs.getLong("match_participant.match_id") != participant.getMatchId()
                    || rs.getLong("match_participant.player_character_id") != participant.getPlayerCharacterId())
                    break;
                team.getMembers().add(LadderSearchDAO.getLadderTeamMemberMapper().mapRow(rs, 0));
            }
            while(rs.next());
            return new LadderMatchParticipant(participant, team, state, twitchUrl, subOnlyVod);
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
                LadderMatch match = new LadderMatch
                (
                    MatchDAO.getStdRowMapper().mapRow(rs, 0),
                    SC2MapDAO.STD_ROW_MAPPER.mapRow(rs, 0),
                    new ArrayList<>()
                );
                matches.add(match);
                while(!rs.isAfterLast() && rs.getLong("match.id") == match.getMatch().getId())
                    match.getParticipants().add(getParticipantExtractor().extractData(rs));
            }
            while(!rs.isAfterLast());
            return matches;
        };
    }

    private void initMisc()
    {
        if(DEFAULT_VERSUS_DECISIONS == null) DEFAULT_VERSUS_DECISIONS = Stream
            .of(BaseMatch.Decision.WIN, BaseMatch.Decision.LOSS)
            .map(d->conversionService.convert(d, Integer.class))
            .collect(Collectors.toList());
        if(DEFAULT_VERSUS_EXCLUDE_TYPES == null) DEFAULT_VERSUS_EXCLUDE_TYPES = Stream
            .of(BaseMatch.MatchType.COOP)
            .map(t->conversionService.convert(t, Integer.class))
            .collect(Collectors.toList());
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

    public void setResultsPerPage(int resultsPerPage)
    {
        this.resultsPerPage = resultsPerPage;
    }

    public PagedSearchResult<List<LadderMatch>> findMatchesByCharacterId
    (
        long characterId,
        OffsetDateTime dateAnchor,
        BaseMatch.MatchType typeAnchor,
        int mapAnchor,
        int page,
        int pageDiff,
        BaseMatch.MatchType... types
    )
    {
        if(Math.abs(pageDiff) != 1) throw new IllegalArgumentException("Invalid page diff");
        boolean forward = pageDiff > -1;
        long finalPage = page + pageDiff;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("playerCharacterId", characterId)
            .addValue("limit", getResultsPerPage());
        addMatchCursorParams(dateAnchor, typeAnchor, mapAnchor, types, params);

        String q = forward ? FIND_MATCHES_BY_CHARACTER_ID : FIND_MATCHES_BY_CHARACTER_ID_REVERSED;
        List<LadderMatch> matches = template.query(q, params, MATCHES_EXTRACTOR);
        return new PagedSearchResult<>(null, (long) getResultsPerPage(), finalPage, matches);
    }

    public PagedSearchResult<List<LadderMatch>> findTwitchVods
    (
        Race race, Race versusRace,
        Integer minRating, Integer maxRating,
        Integer minDuration, Integer maxDuration,
        boolean includeSubOnly,
        Integer mapId,
        OffsetDateTime dateAnchor,
        BaseMatch.MatchType typeAnchor,
        int mapAnchor,
        int page,
        int pageDiff
    )
    {
        if(Math.abs(pageDiff) != 1) throw new IllegalArgumentException("Invalid page diff");
        long finalPage = page + pageDiff;

        String raceStr = Stream.of(race, versusRace)
            .filter(Objects::nonNull)
            .mapToInt(r->conversionService.convert(r, Integer.class))
            .sorted()
            .mapToObj(String::valueOf)
            .collect(Collectors.joining(""));
        raceStr = raceStr.isEmpty() ? null : "%" + raceStr + "%";
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("matchType", conversionService.convert(BaseMatch.MatchType._1V1, Integer.class))
            .addValue("race", raceStr)
            .addValue("minRating", minRating)
            .addValue("maxRating", maxRating)
            .addValue("minDuration", minDuration)
            .addValue("maxDuration", maxDuration)
            .addValue("includeSubOnly", includeSubOnly)
            .addValue("mapId", mapId)
            .addValue("limit", getResultsPerPage());
        addMatchCursorParams
        (
            dateAnchor, typeAnchor, mapAnchor, new BaseMatch.MatchType[]{BaseMatch.MatchType._1V1},
            params
        );
        List<LadderMatch> matches = template.query(FIND_TWITCH_VODS, params, MATCHES_EXTRACTOR);
        return new PagedSearchResult<>(null, (long) getResultsPerPage(), finalPage, matches);
    }

    private MapSqlParameterSource addMatchCursorParams
    (
        OffsetDateTime dateAnchor,
        BaseMatch.MatchType typeAnchor,
        int mapAnchor,
        BaseMatch.MatchType[] types,
        MapSqlParameterSource params
    )
    {
        return params
            .addValue("dateAnchor", dateAnchor)
            .addValue("typeAnchor", conversionService.convert(typeAnchor, Integer.class))
            .addValue("mapIdAnchor", mapAnchor)
            .addValue("cheaterReportType", conversionService
                .convert(PlayerCharacterReport.PlayerCharacterReportType.CHEATER, Integer.class))
            .addValue("types", Arrays.stream(types)
                .map(t->conversionService.convert(t, Integer.class))
                .toArray(Integer[]::new));
    }

    public VersusSummary getVersusSummary
    (
        Integer[] clans1,
        Set<TeamLegacyUid> teams1,
        Integer[] clans2,
        Set<TeamLegacyUid> teams2,
        BaseMatch.MatchType... types
    )
    {
        if((clans1.length == 0 && teams1.isEmpty()) || (clans2.length == 0 && teams2.isEmpty()))
            return new VersusSummary(0, 0, 0);

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("loss", conversionService.convert(BaseMatch.Decision.LOSS, Integer.class))
            .addValue("types", Arrays.stream(types)
                .map(t->conversionService.convert(t, Integer.class))
                .toArray(Integer[]::new));
        addVersusParams(clans1, teams1, clans2, teams2, params);
        return template.query(VERSUS_SUMMARY, params, VERSUS_SUMMARY_EXTRACTOR);
    }

    public PagedSearchResult<List<LadderMatch>> findVersusMatches
    (
        Integer[] clans1,
        Set<TeamLegacyUid> teams1,
        Integer[] clans2,
        Set<TeamLegacyUid> teams2,
        OffsetDateTime dateAnchor,
        BaseMatch.MatchType typeAnchor,
        int mapAnchor,
        int page,
        int pageDiff,
        BaseMatch.MatchType... types
    )
    {
        if(Math.abs(pageDiff) != 1) throw new IllegalArgumentException("Invalid page diff");
        if((clans1.length == 0 && teams1.isEmpty()) || (clans2.length == 0 && teams2.isEmpty()))
            return new PagedSearchResult<>(0L, (long) getResultsPerPage(), 1L, List.of());

        boolean forward = pageDiff > -1;
        long finalPage = page + pageDiff;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("limit", getResultsPerPage());
        addVersusParams(clans1, teams1, clans2, teams2, params);
        addMatchCursorParams(dateAnchor, typeAnchor, mapAnchor, types, params);
        String q = forward ? FIND_VERSUS_MATCHES : FIND_VERSUS_MATCHES_REVERSED;
        List<LadderMatch> matches = template.query(q, params, MATCHES_EXTRACTOR);
        return new PagedSearchResult<>(null, (long) getResultsPerPage(), finalPage, matches);
    }

    private MapSqlParameterSource addVersusParams
    (
        Integer[] clans1,
        Set<TeamLegacyUid> teams1,
        Integer[] clans2,
        Set<TeamLegacyUid> teams2,
        MapSqlParameterSource params
    )
    {
        List<Object[]> teamIds1 = teams1.stream()
            .map(id->new Object[]{
                conversionService.convert(id.getQueueType(), Integer.class),
                conversionService.convert(id.getRegion(), Integer.class),
                id.getId()
            })
            .collect(Collectors.toList());
        List<Object[]> teamIds2 = teams2.stream()
            .map(id->new Object[]{
                conversionService.convert(id.getQueueType(), Integer.class),
                conversionService.convert(id.getRegion(), Integer.class),
                id.getId()
            })
            .collect(Collectors.toList());
        Integer[] clanIds = Stream.of(clans1, clans2)
            .flatMap(Stream::of)
            .toArray(Integer[]::new);
        List<Object[]> teamIds = Stream.of(teamIds1, teamIds2)
            .flatMap(List::stream)
            .collect(Collectors.toList());
        return params
            .addValue("clans", clanIds)
            .addValue("teams", teamIds.isEmpty() ? null : teamIds)
            .addValue("clans1", clans1)
            .addValue("teams1", teamIds1.isEmpty() ? null : teamIds1)
            .addValue("clans2", clans2)
            .addValue("teams2", teamIds2.isEmpty() ? null : teamIds2)
            .addValue("validDecisions", DEFAULT_VERSUS_DECISIONS)
            .addValue("excludeTypes", DEFAULT_VERSUS_EXCLUDE_TYPES);
    }

}
