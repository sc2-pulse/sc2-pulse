// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.LeagueTier;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterReport;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.DAOUtils;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.PopulationStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamMemberDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyId;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.model.local.ladder.PagedSearchResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

@Repository
public class LadderSearchDAO
{

    private static final String FIND_TEAM_MEMBERS_BASE =
        "team_member.terran_games_played, team_member.protoss_games_played, "
        + "team_member.zerg_games_played, team_member.random_games_played, "
        + TeamDAO.STD_SELECT + ", "
        + PopulationStateDAO.TEAM_DATA_SELECT + ", "
        + PlayerCharacterDAO.STD_SELECT + ", "
        + AccountDAO.STD_SELECT + ", "
        + ClanDAO.STD_SELECT + ", "
        + "pro_player.id AS \"pro_player.id\", "
        + "pro_player.nickname AS \"pro_player.nickname\", "
        + "COALESCE(pro_team.short_name, pro_team.name) AS \"pro_player.team\", "
        + "confirmed_cheater_report.restrictions AS \"confirmed_cheater_report.restrictions\" ";

    private static final String LADDER_SEARCH_TEAM_FROM_HEAD =
        "INNER JOIN team ON team_member.team_id=team.id "
        + "LEFT JOIN population_state ON team.population_state_id = population_state.id "
        + "INNER JOIN player_character ON team_member.player_character_id=player_character.id "
        + "INNER JOIN account ON player_character.account_id=account.id ";
    private static final String LADDER_SEARCH_TEAM_FROM_SHORT =
        "FROM team_member "
        + LADDER_SEARCH_TEAM_FROM_HEAD;

    private static final String LADDER_SEARCH_TEAM_FROM_FULL_BODY =
        "LEFT JOIN clan_member ON player_character.id = clan_member.player_character_id "
        + "LEFT JOIN clan ON clan_member.clan_id = clan.id "
        + "LEFT JOIN pro_player_account ON account.id=pro_player_account.account_id "
        + "LEFT JOIN pro_player ON pro_player_account.pro_player_id=pro_player.id "
        + "LEFT JOIN pro_team_member ON pro_player.id=pro_team_member.pro_player_id "
        + "LEFT JOIN pro_team ON pro_team_member.pro_team_id=pro_team.id "
        + "LEFT JOIN player_character_report AS confirmed_cheater_report "
        + "ON player_character.id = confirmed_cheater_report.player_character_id "
        + "AND confirmed_cheater_report.type = :cheaterReportType "
        + "AND confirmed_cheater_report.status = true ";

    private static final String LADDER_SEARCH_TEAM_FROM = LADDER_SEARCH_TEAM_FROM_SHORT + LADDER_SEARCH_TEAM_FROM_FULL_BODY;

    private static final String LADDER_SEARCH_TEAM_WHERE =
        "WHERE "
        + "team.season=:seasonId "
        + "AND team.region IN (:regions) "
        + "AND team.league_type IN (:leagueTypes) "
        + "AND team.queue_type=:queueType "
        + "AND team.team_type=:teamType ";
    private static final String LADDER_SEARCH_TEAM_FROM_WHERE =
        LADDER_SEARCH_TEAM_FROM + LADDER_SEARCH_TEAM_WHERE;

    private static final String FIND_FOLLOWING_TEAM_MEMBERS =
        "WITH following_team AS"
        + "("
            + "SELECT DISTINCT(team.id) "
            + LADDER_SEARCH_TEAM_FROM_SHORT
            + "INNER JOIN account_following ON account.id=account_following.following_account_id "
            + LADDER_SEARCH_TEAM_WHERE
            + "AND account_following.account_id=:accountId "
        + ") "
        + "SELECT "
        + FIND_TEAM_MEMBERS_BASE
        + "FROM following_team "
        + "INNER JOIN team ON following_team.id = team.id "
        + "LEFT JOIN population_state ON team.population_state_id = population_state.id "
        + "INNER JOIN team_member ON team_member.team_id = team.id "
        + "INNER JOIN player_character ON team_member.player_character_id=player_character.id "
        + "INNER JOIN account ON player_character.account_id=account.id "
        + "LEFT JOIN clan_member ON player_character.id = clan_member.player_character_id "
        + "LEFT JOIN clan ON clan_member.clan_id = clan.id "
        + "LEFT JOIN pro_player_account ON account.id=pro_player_account.account_id "
        + "LEFT JOIN pro_player ON pro_player_account.pro_player_id=pro_player.id "
        + "LEFT JOIN pro_team_member ON pro_player.id=pro_team_member.pro_player_id "
        + "LEFT JOIN pro_team ON pro_team_member.pro_team_id=pro_team.id "
        + "LEFT JOIN player_character_report AS confirmed_cheater_report "
            + "ON player_character.id = confirmed_cheater_report.player_character_id "
            + "AND confirmed_cheater_report.type = :cheaterReportType "
            + "AND confirmed_cheater_report.status = true "
        + "ORDER BY team.rating DESC, team.id DESC";

    private static final String FIND_TEAM_MEMBERS_ANCHOR_FORMAT =
        "SELECT "
        + FIND_TEAM_MEMBERS_BASE

        + LADDER_SEARCH_TEAM_FROM_WHERE
        + "AND (team.rating, team.id) %2$s (:ratingAnchor, :idAnchor) "

        + "ORDER BY team.rating %1$s, team.id %1$s "
        + "OFFSET :offset LIMIT :limit";

    private static final String FIND_TEAM_MEMBERS_ANCHOR_QUERY =
        String.format(FIND_TEAM_MEMBERS_ANCHOR_FORMAT, "DESC", "<");

    private static final String FIND_TEAM_MEMBERS_ANCHOR_REVERSED_QUERY =
        String.format(FIND_TEAM_MEMBERS_ANCHOR_FORMAT, "ASC", ">");

    private static final String FIND_CHARACTER_TEAM_MEMBERS_TAIL =
        "SELECT "
        + FIND_TEAM_MEMBERS_BASE

        + "FROM team_filtered "
        + "INNER JOIN team ON team_filtered.id=team.id "
        + "LEFT JOIN population_state ON team.population_state_id = population_state.id "
        + "INNER JOIN team_member ON team.id = team_member.team_id "
        + "INNER JOIN player_character ON team_member.player_character_id=player_character.id "
        + "INNER JOIN account ON player_character.account_id=account.id "
        + "LEFT JOIN clan_member ON player_character.id = clan_member.player_character_id "
        + "LEFT JOIN clan ON clan_member.clan_id = clan.id "
        + "LEFT JOIN pro_player_account ON account.id=pro_player_account.account_id "
        + "LEFT JOIN pro_player ON pro_player_account.pro_player_id=pro_player.id "
        + "LEFT JOIN pro_team_member ON pro_player.id=pro_team_member.pro_player_id "
        + "LEFT JOIN pro_team ON pro_team_member.pro_team_id=pro_team.id "
        + "LEFT JOIN player_character_report AS confirmed_cheater_report "
            + "ON player_character.id = confirmed_cheater_report.player_character_id "
            + "AND confirmed_cheater_report.type = :cheaterReportType "
            + "AND confirmed_cheater_report.status = true ";

    private static final String SEARCH_ORDER = "ORDER BY team.season DESC, "
        + "team.queue_type ASC, team.team_type ASC, team.rating DESC, team.id ASC, "
        + "player_character.id ASC ";
    private static final String QUEUE_ORDER = "ORDER BY team.season DESC, "
        + "team.queue_type ASC, team.rating DESC, team.id ASC, "
        + "player_character.id ASC ";

    private static final String CHARACTER_TEAM_IDS_UNORDERED_QUERY =
        "SELECT DISTINCT(team_id) AS id "
        + "FROM team_member "
        + "WHERE player_character_id IN(:playerCharacterIds) "
        + "AND (array_length(:seasons::smallint[], 1) IS NULL OR team_season = ANY(:seasons)) "
        + "AND (array_length(:queues::smallint[], 1) IS NULL OR team_queue_type = ANY(:queues)) "
        + "AND "
        + "("
            + "array_length(:races::smallint[], 1) IS NULL "
            + "OR " + TeamMemberDAO.FAVORITE_RACE_SELECT + " = ANY(:races)"
        + ") ";
    private static final String FIND_CHARACTER_TEAM_MEMBERS_QUERY =
        "WITH team_filtered AS (" + CHARACTER_TEAM_IDS_UNORDERED_QUERY + ") "
        + FIND_CHARACTER_TEAM_MEMBERS_TAIL
        + QUEUE_ORDER;

    private static final String FIND_CHARACTER_TEAM_MEMBERS_LIMIT_QUERY =
        "WITH team_unordered_filter AS (" + CHARACTER_TEAM_IDS_UNORDERED_QUERY + "), "
        + "team_filtered AS "
        + "( "
            + "SELECT team.id "
            + "FROM team_unordered_filter "
            + "INNER JOIN team USING(id) "
            + "ORDER BY team.season DESC, "
            + "team.queue_type ASC, team.team_type ASC, team.rating DESC, team.id ASC "
            + "LIMIT :limit"
        + ") "
        + FIND_CHARACTER_TEAM_MEMBERS_TAIL
        + QUEUE_ORDER;

    private static final String FIND_LEGACY_TEAM_MEMBERS =
        "SELECT "
        + FIND_TEAM_MEMBERS_BASE
        + LADDER_SEARCH_TEAM_FROM
        + "WHERE (team.queue_type, team.team_type, team.region, team.legacy_id) IN (:legacyUids) "
        + "ORDER BY team.season, team.id";

    private static final String FIND_FIRST_LEGACY_TEAM_MEMBERS =
        "WITH "
        + "team_filter AS "
        + "("
            + "SELECT DISTINCT ON(team.queue_type, team.team_type, team.region, team.legacy_id) "
            + "id "
            + "FROM team "
            + "WHERE (team.queue_type, team.team_type, team.region, team.legacy_id) IN (:legacyUids) "
            + "ORDER BY team.queue_type DESC, "
            + "team.team_type DESC, "
            + "team.region DESC, "
            + "team.legacy_id DESC, "
            + "team.season DESC"
        + ") "
        + "SELECT "
        + FIND_TEAM_MEMBERS_BASE
        + "FROM team_filter "
        + "INNER JOIN team ON team_filter.id = team.id "
        + "LEFT JOIN population_state ON team.population_state_id = population_state.id "
        + "INNER JOIN team_member ON team_member.team_id = team.id "
        + "INNER JOIN player_character ON team_member.player_character_id=player_character.id "
        + "INNER JOIN account ON player_character.account_id=account.id "
        + LADDER_SEARCH_TEAM_FROM_FULL_BODY;

    private static final String FIND_RECENTLY_ACTIVE_TEAMS =
        "WITH team_filter AS "
        + "("
            + "SELECT id AS team_id "
            + "FROM team "
            + "WHERE team.queue_type = :queueType "
            + "AND team.league_type = :leagueType "
            + "AND team.last_played >= :lastPlayedMin "
            + "AND (:winsMin::integer IS NULL OR team.wins >= :winsMin::integer) "
            + "AND (:winsMax::integer IS NULL OR team.wins <= :winsMax::integer) "
            + "AND (:ratingMin::integer IS NULL OR team.rating >= :ratingMin::integer) "
            + "AND (:ratingMax::integer IS NULL OR team.rating <= :ratingMax::integer) "
            + "AND (:race::integer IS NULL "
                + "OR ("
                    + "team.queue_type = " + QueueType.LOTV_1V1.getId() + " "
                    + "AND substring(team.legacy_id::text from char_length(team.legacy_id::text))::smallint"
                        + "= :race"
                + ") "
            + ") "
            + "AND (:region::smallint IS NULL OR team.region = :region::smallint) "
            + "ORDER BY team.queue_type DESC, team.league_type DESC, team.last_played DESC "
            + "LIMIT :limit"
        + ") "
        + "SELECT "
        + FIND_TEAM_MEMBERS_BASE
        + "FROM team_filter "
        + "INNER JOIN team_member USING(team_id) "
        + LADDER_SEARCH_TEAM_FROM_HEAD
        + LADDER_SEARCH_TEAM_FROM_FULL_BODY
        + "ORDER BY team.last_played DESC, team.id DESC";

    private static final String FIND_TEAMS_BY_IDS =
        "SELECT "
        + FIND_TEAM_MEMBERS_BASE
        + "FROM team_member "
        + LADDER_SEARCH_TEAM_FROM_HEAD
        + LADDER_SEARCH_TEAM_FROM_FULL_BODY
        + "WHERE team_member.team_id IN(:ids)";

    private NamedParameterJdbcTemplate template;
    private ConversionService conversionService;
    private SeasonDAO seasonDAO;

    private static RowMapper<LadderTeamMember> LADDER_TEAM_MEMBER_MAPPER;
    private static RowMapper<LadderTeam> LADDER_TEAM_MAPPER;
    private static ResultSetExtractor<LadderTeam> LADDER_TEAM_EXTRACTOR;
    private final ResultSetExtractor<List<LadderTeam>> LADDER_TEAMS_EXTRACTOR = this::mapTeams;

    private int resultsPerPage = 100;

    @Autowired @Lazy
    private LadderSearchDAO ladderSearchDAO;

    LadderSearchDAO(){}

    @Autowired
    public LadderSearchDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService,
        @Autowired SeasonDAO seasonDAO
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        this.seasonDAO = seasonDAO;
        initMappers(conversionService);
    }

    private static void initMappers(ConversionService conversionService)
    {
        initLadderTeamMemberMapper();
        initLadderTeamMapper(conversionService);
        initLadderTeamExtractor();
    }

    private static void initLadderTeamMemberMapper()
    {
        if(LADDER_TEAM_MEMBER_MAPPER == null) LADDER_TEAM_MEMBER_MAPPER = (rs, i)->
        {
            Clan clan = DAOUtils.getInteger(rs, "clan.id") == null
                ? null
                : ClanDAO.getStdRowMapper().mapRow(rs, i);
            return new LadderTeamMember
            (
                AccountDAO.getStdRowMapper().mapRow(rs, 0),
                PlayerCharacterDAO.getStdRowMapper().mapRow(rs, 0),
                clan,
                DAOUtils.getLong(rs, "pro_player.id"),
                rs.getString("pro_player.nickname"),
                rs.getString("pro_player.team"),
                DAOUtils.getBoolean(rs, "confirmed_cheater_report.restrictions"),
                (Integer) rs.getObject("terran_games_played"),
                (Integer) rs.getObject("protoss_games_played"),
                (Integer) rs.getObject("zerg_games_played"),
                (Integer) rs.getObject("random_games_played")
            );
        };
    }

    private static void initLadderTeamMapper(ConversionService conversionService)
    {
        if(LADDER_TEAM_MAPPER == null) LADDER_TEAM_MAPPER = (rs, i)->
        {
            LadderTeam team = new LadderTeam(
                rs.getLong("team.id"),
                rs.getInt("team.season"),
                conversionService.convert(rs.getInt("team.region"), Region.class),
                new BaseLeague(
                    conversionService.convert(rs.getInt("team.league_type"), League.LeagueType.class),
                    conversionService.convert(rs.getInt("team.queue_type"), QueueType.class),
                    conversionService.convert(rs.getInt("team.team_type"), TeamType.class)
                ),
                conversionService.convert(DAOUtils.getInteger(rs, "team.tier_type"), LeagueTier.LeagueTierType.class),
                TeamLegacyId.trusted(rs.getString("team.legacy_id")),
                rs.getInt("team.division_id"),
                rs.getLong("team.rating"),
                rs.getInt("team.wins"),
                rs.getInt("team.losses"),
                rs.getInt("team.ties"),
                null,
                rs.getObject("team.last_played", OffsetDateTime.class),
                rs.getObject("team.joined", OffsetDateTime.class),
                rs.getObject("team.primary_data_updated", OffsetDateTime.class),
                new ArrayList<>(),
                PopulationStateDAO.TEAM_DATA_ROW_MAPPER.mapRow(rs, 1)
            );
            team.setGlobalRank(rs.getInt("team.global_rank"));
            team.setRegionRank(rs.getInt("team.region_rank"));
            team.setLeagueRank(rs.getInt("team.league_rank"));
            return team;
        };
    }

    private static void initLadderTeamExtractor()
    {
        if(LADDER_TEAM_EXTRACTOR == null) LADDER_TEAM_EXTRACTOR = rs->
        {
            if(rs.isAfterLast()) return null;

            LadderTeam team = getLadderTeamMapper().mapRow(rs, 0);
            do {
                if (rs.getLong("team.id") != team.getId()) break;
                team.getMembers().add(getLadderTeamMemberMapper().mapRow(rs, 0));
            } while (rs.next());

            return team;
        };
    }

    public static RowMapper<LadderTeamMember> getLadderTeamMemberMapper()
    {
        return LADDER_TEAM_MEMBER_MAPPER;
    }
    public static RowMapper<LadderTeam> getLadderTeamMapper()
    {
        return LADDER_TEAM_MAPPER;
    }

    public static ResultSetExtractor<LadderTeam> getLadderTeamExtractor()
    {
        return LADDER_TEAM_EXTRACTOR;
    }

    protected void setResultsPerPage(int resultsPerPage)
    {
        this.resultsPerPage = resultsPerPage;
    }
    
    public int getResultsPerPage()
    {
        return resultsPerPage;
    }

    public LadderSearchDAO getLadderSearchDAO()
    {
        return ladderSearchDAO;
    }

    protected void setLadderSearchDAO(LadderSearchDAO ladderSearchDAO)
    {
        this.ladderSearchDAO = ladderSearchDAO;
    }

    public SeasonDAO getSeasonDAO()
    {
        return seasonDAO;
    }

    private List<LadderTeam> mapTeams(ResultSet rs)
    throws SQLException
    {
        List<LadderTeam> teams = new ArrayList<>();
        if(!rs.next()) return teams;
        while(!rs.isAfterLast()) teams.add(getLadderTeamExtractor().extractData(rs));
        return teams;
    }

    public PagedSearchResult<List<LadderTeam>> findAnchored
    (
        int season,
        Set<Region> regions,
        Set<League.LeagueType> leagueTypes,
        QueueType queueType,
        TeamType teamType,
        long page,
        long ratingAnchor,
        long idAnchor,
        int pageDiff
    )
    {
        boolean forward = pageDiff > -1;
        long finalPage = page + pageDiff;
        long membersPerTeam = queueType.getTeamFormat().getMemberCount(teamType);
        long offset = (Math.abs(pageDiff) - 1) * getResultsPerPage() * membersPerTeam;
        long limit = getResultsPerPage() * membersPerTeam;
        MapSqlParameterSource params =
            LadderUtil.createSearchParams(conversionService, season, regions, leagueTypes, queueType, teamType)
                .addValue("offset", offset)
                .addValue("limit", limit)
                .addValue("ratingAnchor", ratingAnchor)
                .addValue("idAnchor", idAnchor)
                .addValue("cheaterReportType", conversionService
                    .convert(PlayerCharacterReport.PlayerCharacterReportType.CHEATER, Integer.class));

        String q = forward ? FIND_TEAM_MEMBERS_ANCHOR_QUERY : FIND_TEAM_MEMBERS_ANCHOR_REVERSED_QUERY;
        List<LadderTeam> teams = template
            .query(q, params, LADDER_TEAMS_EXTRACTOR);
        if(!forward) Collections.reverse(teams);

        return new PagedSearchResult<>(null, (long) getResultsPerPage(), finalPage, teams);
    }

    @Cacheable(cacheNames = "fqdn-ladder-scan", keyGenerator = "fqdnSimpleKeyGenerator")
    public List<Season> findSeasonList()
    {
        return seasonDAO.findListByFirstBattlenetId();
    }

    public List<LadderTeam> findCharacterTeams
    (
        Set<Long> ids,
        Set<Integer> seasons,
        Set<QueueType> queues,
        Set<Race> races,
        Integer limit
    )
    {
        if(ids.isEmpty()) return List.of();

        Integer[] queueIds = queues.isEmpty()
            ? null
            : queues.stream()
                .map(q->conversionService.convert(q, Integer.class))
                .toArray(Integer[]::new);
        Integer[] raceIds = races.isEmpty()
            ? null
            : races.stream()
                .map(r->conversionService.convert(r, Integer.class))
                .toArray(Integer[]::new);
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("playerCharacterIds", ids)
            .addValue("seasons", seasons.isEmpty() ? null : seasons.toArray(Integer[]::new))
            .addValue("queues", queueIds)
            .addValue("races", raceIds)
            .addValue("limit", limit)
            .addValue("cheaterReportType", conversionService
                .convert(PlayerCharacterReport.PlayerCharacterReportType.CHEATER, Integer.class));
        String query = limit != null
            ? FIND_CHARACTER_TEAM_MEMBERS_LIMIT_QUERY
            : FIND_CHARACTER_TEAM_MEMBERS_QUERY;
        return template.query(query, params, LADDER_TEAMS_EXTRACTOR);
    }

    public List<LadderTeam> findCharacterTeams
    (
        Long id,
        Set<Integer> seasons,
        Set<QueueType> queues,
        Set<Race> races,
        Integer limit
    )
    {
        return findCharacterTeams(Set.of(id), seasons, queues, races, limit);
    }

    public List<LadderTeam> findCharacterTeams(Set<Long> ids)
    {
        return findCharacterTeams(ids, Set.of(), Set.of(), Set.of(), null);
    }

    public List<LadderTeam> findFollowingTeams
    (
        Long accountId,
        int season,
        Set<Region> regions,
        Set<League.LeagueType> leagueTypes,
        QueueType queueType,
        TeamType teamType
    )
    {
        MapSqlParameterSource params =
            LadderUtil.createSearchParams(conversionService, season, regions, leagueTypes, queueType, teamType)
            .addValue("accountId", accountId)
            .addValue("cheaterReportType", conversionService
                .convert(PlayerCharacterReport.PlayerCharacterReportType.CHEATER, Integer.class));
        return template
            .query(FIND_FOLLOWING_TEAM_MEMBERS, params, LADDER_TEAMS_EXTRACTOR);
    }

    public List<LadderTeam> findLegacyTeams(Set<TeamLegacyUid> ids, boolean all)
    {
        if(ids.isEmpty()) return new ArrayList<>();

        List<Object[]> legacyUids = ids.stream()
            .map(id->new Object[]{
                conversionService.convert(id.getQueueType(), Integer.class),
                conversionService.convert(id.getTeamType(), Integer.class),
                conversionService.convert(id.getRegion(), Integer.class),
                id.getId().getId()
            })
            .collect(Collectors.toList());
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("legacyUids", legacyUids)
            .addValue("cheaterReportType", conversionService
                .convert(PlayerCharacterReport.PlayerCharacterReportType.CHEATER, Integer.class));
        String query = all ? FIND_LEGACY_TEAM_MEMBERS : FIND_FIRST_LEGACY_TEAM_MEMBERS;
        return template.query(query, params, LADDER_TEAMS_EXTRACTOR);
    }

    public List<LadderTeam> findRecentlyActiveTeams
    (
        @NonNull QueueType queueType,
        @NonNull BaseLeague.LeagueType leagueType,
        @NonNull OffsetDateTime lastPlayedMin,
        @Nullable Integer winsMin,
        @Nullable Integer winsMax,
        @Nullable Integer ratingMin,
        @Nullable Integer ratingMax,
        @Nullable Race race,
        @Nullable Region region,
        int limit
    )
    {
        Objects.requireNonNull(queueType);
        Objects.requireNonNull(leagueType);
        Objects.requireNonNull(lastPlayedMin);
        if(winsMin != null && winsMax != null && winsMax < winsMin)
            throw new IllegalArgumentException("Wins max is less than wins min");
        if(ratingMin != null && ratingMax != null && ratingMax < ratingMin)
            throw new IllegalArgumentException("Rating max is less than rating min");
        if(limit < 0) throw new IllegalArgumentException("Limit must be positive");
        if(race != null && queueType != QueueType.LOTV_1V1)
            throw new IllegalArgumentException("Race can only be used with 1v1 queue");

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("queueType", conversionService.convert(queueType, Integer.class))
            .addValue("leagueType", conversionService.convert(leagueType, Integer.class))
            .addValue("lastPlayedMin", lastPlayedMin)
            .addValue("winsMin", winsMin)
            .addValue("winsMax", winsMax)
            .addValue("ratingMin", ratingMin)
            .addValue("ratingMax", ratingMax)
            .addValue("race", conversionService.convert(race, Integer.class))
            .addValue("region", conversionService.convert(region, Integer.class))
            .addValue("limit", limit)
            .addValue("cheaterReportType", conversionService
                .convert(PlayerCharacterReport.PlayerCharacterReportType.CHEATER, Integer.class));
        return template.query(FIND_RECENTLY_ACTIVE_TEAMS, params, LADDER_TEAMS_EXTRACTOR);
    }

    public List<LadderTeam> findTeamsByIds(Set<Long> ids)
    {
        if(ids.isEmpty()) return List.of();

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("ids", ids)
            .addValue("cheaterReportType", conversionService
                .convert(PlayerCharacterReport.PlayerCharacterReportType.CHEATER, Integer.class));
        return template.query(FIND_TEAMS_BY_IDS, params, LADDER_TEAMS_EXTRACTOR);
    }

}
