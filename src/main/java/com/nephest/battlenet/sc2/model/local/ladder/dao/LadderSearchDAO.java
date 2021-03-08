// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.LeagueTier;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.dao.*;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.model.local.ladder.PagedSearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Repository
public class LadderSearchDAO
{

    private static final String FIND_TEAM_MEMBERS_BASE =
        "SELECT "
        + TeamDAO.STD_SELECT + ", "
        + "pro_player.nickname AS \"pro_player.nickname\", "
        + "COALESCE(pro_team.short_name, pro_team.name) AS \"pro_player.team\","
        + AccountDAO.STD_SELECT + ", "
        + PlayerCharacterDAO.STD_SELECT + ", "
        + "team_member.terran_games_played, team_member.protoss_games_played, "
        + "team_member.zerg_games_played, team_member.random_games_played ";

    private static final String LADDER_SEARCH_TEAM_FROM_SHORT =
        "FROM team_member "
        + "INNER JOIN team ON team_member.team_id=team.id "
        + "INNER JOIN player_character ON team_member.player_character_id=player_character.id "
        + "INNER JOIN account ON player_character.account_id=account.id ";

    private static final String LADDER_SEARCH_TEAM_FROM =
        LADDER_SEARCH_TEAM_FROM_SHORT
        + "LEFT JOIN pro_player_account ON account.id=pro_player_account.account_id "
        + "LEFT JOIN pro_player ON pro_player_account.pro_player_id=pro_player.id "
        + "LEFT JOIN pro_team_member ON pro_player.id=pro_team_member.pro_player_id "
        + "LEFT JOIN pro_team ON pro_team_member.pro_team_id=pro_team.id ";

    private static final String LADDER_SEARCH_TEAM_WHERE =
        "WHERE "
        + "team.season=:seasonId "
        + "AND team.region IN (:region0, :region1, :region2, :region3) "
        + "AND team.league_type IN (:leagueType0, :leagueType1, :leagueType2, :leagueType3, :leagueType4, :leagueType5, :leagueType6) "
        + "AND team.queue_type=:queueType "
        + "AND team.team_type=:teamType ";

    private static final String LADDER_SEARCH_TEAM_FROM_WHERE =
        LADDER_SEARCH_TEAM_FROM + LADDER_SEARCH_TEAM_WHERE;

    private static final String FIND_TEAM_MEMBERS_LATE_FORMAT =
        FIND_TEAM_MEMBERS_BASE

        + "FROM "
        + "(SELECT team_id, player_character_id "
            + "FROM team_member "
            + "INNER JOIN team teamr ON team_member.team_id=teamr.id "
            + "WHERE "
            + "teamr.season=:seasonId "
            + "AND teamr.region IN (:region0, :region1, :region2, :region3) "
            + "AND teamr.league_type IN (:leagueType0, :leagueType1, :leagueType2, :leagueType3, :leagueType4, :leagueType5, :leagueType6) "
            + "AND teamr.queue_type=:queueType "
            + "AND teamr.team_type=:teamType "
            + "ORDER BY teamr.rating %1$s, teamr.id %1$s "
            + "OFFSET :offset LIMIT :limit"
        + ") o "
        + "JOIN team_member ON team_member.team_id=o.team_id AND team_member.player_character_id=o.player_character_id "
        + "INNER JOIN team ON team_member.team_id=team.id "
        + "INNER JOIN player_character ON team_member.player_character_id=player_character.id "
        + "INNER JOIN account ON player_character.account_id=account.id "
        + "LEFT JOIN pro_player_account ON account.id=pro_player_account.account_id "
        + "LEFT JOIN pro_player ON pro_player_account.pro_player_id=pro_player.id "
        + "LEFT JOIN pro_team_member ON pro_player.id=pro_team_member.pro_player_id "
        + "LEFT JOIN pro_team ON pro_team_member.pro_team_id=pro_team.id "
        + "ORDER BY team.rating %1$s, team.id %1$s ";

    private static final String FIND_TEAM_MEMBERS_LATE_QUERY =
        String.format(FIND_TEAM_MEMBERS_LATE_FORMAT, "DESC");

    private static final String FIND_TEAM_MEMBERS_LATE_REVERSED_QUERY =
        String.format(FIND_TEAM_MEMBERS_LATE_FORMAT, "ASC");

    private static final String FIND_TEAM_MEMBERS_FORMAT =
        FIND_TEAM_MEMBERS_BASE

        + LADDER_SEARCH_TEAM_FROM_WHERE

        + "ORDER BY team.rating %1$s, team.id %1$s "
        + "OFFSET :offset LIMIT :limit";

    private static final String FIND_TEAM_MEMBERS_QUERY =
        String.format(FIND_TEAM_MEMBERS_FORMAT, "DESC");

    private static final String FIND_TEAM_MEMBERS_REVERSED_QUERY =
        String.format(FIND_TEAM_MEMBERS_FORMAT, "ASC");

    private static final String FIND_FOLLOWING_TEAM_MEMBERS =
        "WITH following_team AS"
        + "("
            + "SELECT DISTINCT(team.id) "
            + LADDER_SEARCH_TEAM_FROM_SHORT
            + "INNER JOIN account_following ON account.id=account_following.following_account_id "
            + LADDER_SEARCH_TEAM_WHERE
            + "AND account_following.account_id=:accountId "
        + ") "
        + FIND_TEAM_MEMBERS_BASE
        + "FROM following_team "
        + "INNER JOIN team ON following_team.id = team.id "
        + "INNER JOIN team_member ON team_member.team_id = team.id "
        + "INNER JOIN player_character ON team_member.player_character_id=player_character.id "
        + "INNER JOIN account ON player_character.account_id=account.id "
        + "LEFT JOIN pro_player_account ON account.id=pro_player_account.account_id "
        + "LEFT JOIN pro_player ON pro_player_account.pro_player_id=pro_player.id "
        + "LEFT JOIN pro_team_member ON pro_player.id=pro_team_member.pro_player_id "
        + "LEFT JOIN pro_team ON pro_team_member.pro_team_id=pro_team.id "
        + "ORDER BY team.rating DESC, team.id DESC";

    private static final String FIND_TEAM_MEMBERS_ANCHOR_FORMAT =
        FIND_TEAM_MEMBERS_BASE

        + LADDER_SEARCH_TEAM_FROM_WHERE
        + "AND (team.rating, team.id) %2$s (:ratingAnchor, :idAnchor) "

        + "ORDER BY team.rating %1$s, team.id %1$s "
        + "OFFSET :offset LIMIT :limit";

    private static final String FIND_TEAM_MEMBERS_ANCHOR_QUERY =
        String.format(FIND_TEAM_MEMBERS_ANCHOR_FORMAT, "DESC", "<");

    private static final String FIND_TEAM_MEMBERS_ANCHOR_REVERSED_QUERY =
        String.format(FIND_TEAM_MEMBERS_ANCHOR_FORMAT, "ASC", ">");

    private static final String FIND_TEAM_COUNT_QUERY =
        "SELECT COUNT(*) "
        + "FROM team "
        + LADDER_SEARCH_TEAM_WHERE;

    private static final String FIND_CARACTER_TEAM_MEMBERS_QUERY =
        "WITH team_filtered AS "
        + "( "
            + "SELECT "
            + "team.id "
            + "FROM team "
            + "INNER JOIN team_member ON team_member.team_id=team.id "
            + "INNER JOIN player_character ON team_member.player_character_id=player_character.id "
            + "WHERE "
            + "player_character.id=:playerCharacterId "
        + ")"
        + FIND_TEAM_MEMBERS_BASE

        + "FROM team_filtered "
        + "INNER JOIN team ON team_filtered.id=team.id "
        + "INNER JOIN team_member ON team.id = team_member.team_id "
        + "INNER JOIN player_character ON team_member.player_character_id=player_character.id "
        + "INNER JOIN account ON player_character.account_id=account.id "
        + "LEFT JOIN pro_player_account ON account.id=pro_player_account.account_id "
        + "LEFT JOIN pro_player ON pro_player_account.pro_player_id=pro_player.id "
        + "LEFT JOIN pro_team_member ON pro_player.id=pro_team_member.pro_player_id "
        + "LEFT JOIN pro_team ON pro_team_member.pro_team_id=pro_team.id "
        + "INNER JOIN season ON season.battlenet_id=team.season AND season.region=team.region "

        + "ORDER BY team.season DESC, "
        + "team.queue_type ASC, team.team_type ASC, team.league_type DESC, "
        + "team.rating DESC, team.id ASC, "
        + "player_character.id ASC ";

    private NamedParameterJdbcTemplate template;
    private ConversionService conversionService;
    private SeasonDAO seasonDAO;

    private final ResultSetExtractor<List<LadderTeam>> LADDER_TEAM_EXTRACTOR= this::mapTeams;

    private int resultsPerPage = 100;

    private LadderUtil ladderUtil;

    @Autowired @Lazy
    private LadderSearchDAO ladderSearchDAO;

    LadderSearchDAO(){}

    @Autowired
    public LadderSearchDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService,
        @Autowired LadderUtil ladderUtil,
        @Autowired SeasonDAO seasonDAO
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        this.ladderUtil = ladderUtil;
        this.seasonDAO = seasonDAO;
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
        long lastTeamId = -1;
        List<LadderTeam> teams = new ArrayList<>();
        List<LadderTeamMember> members = null;
        while(rs.next())
        {
           // if(totalCount == 0) totalCount = rs.getLong("total_team_count");
            long teamId = rs.getLong("team.id");
            if (teamId != lastTeamId)
            {
                members = new ArrayList<>();
                BigDecimal idDec = (BigDecimal) rs.getObject("team.battlenet_id");
                LadderTeam team = new LadderTeam
                (
                    teamId,
                    rs.getInt("team.season"),
                    conversionService.convert(rs.getInt("team.region"), Region.class),
                    new BaseLeague
                    (
                        conversionService.convert(rs.getInt("team.league_type"), League.LeagueType.class),
                        conversionService.convert(rs.getInt("team.queue_type"), QueueType.class),
                        conversionService.convert(rs.getInt("team.team_type"), TeamType.class)
                    ),
                    conversionService.convert(rs.getInt("team.tier_type"), LeagueTier.LeagueTierType.class),
                    rs.getLong("team.division_id"),
                    idDec == null ? null : idDec.toBigInteger(),
                    rs.getLong("team.rating"),
                    rs.getInt("team.wins"), rs.getInt("team.losses"), rs.getInt("team.ties"),
                    null,
                    members
                );
                team.setGlobalRank(rs.getInt("team.global_rank"));
                team.setRegionRank(rs.getInt("team.region_rank"));
                team.setLeagueRank(rs.getInt("team.league_rank"));
                teams.add(team);
                lastTeamId = teamId;
            }

            LadderTeamMember member = new LadderTeamMember
            (
                AccountDAO.getStdRowMapper().mapRow(rs, 0),
                PlayerCharacterDAO.getStdRowMapper().mapRow(rs, 0),
                rs.getString("pro_player.nickname"),
                rs.getString("pro_player.team"),
                (Integer) rs.getObject("terran_games_played"),
                (Integer) rs.getObject("protoss_games_played"),
                (Integer) rs.getObject("zerg_games_played"),
                (Integer) rs.getObject("random_games_played")
            );
            members.add(member);
        }
        return teams;
    }

    public PagedSearchResult<List<LadderTeam>> find
    (
        int season,
        Set<Region> regions,
        Set<League.LeagueType> leagueTypes,
        QueueType queueType,
        TeamType teamType,
        long page
    )
    {
        long membersPerTeam = queueType.getTeamFormat().getMemberCount(teamType);
        long teamCount = ladderSearchDAO.getTeamCount(season, regions, leagueTypes, queueType, teamType);
        long pageCount = (long) Math.ceil(teamCount /(double) getResultsPerPage());
        long middlePage = (long) Math.ceil(pageCount / 2d);
        long offset;
        long limit = getResultsPerPage() * membersPerTeam;
        if (page < 1) page = 1;
        if(page > pageCount) page = pageCount;
        boolean reversed = page > middlePage;
        if(!reversed)
        {
             offset = (page - 1) * getResultsPerPage() * membersPerTeam;
        }
        else
        {
            if(page == pageCount)
            {
                offset = 0;
                limit = (teamCount % getResultsPerPage()) * membersPerTeam;
                limit = limit == 0 ? getResultsPerPage() * membersPerTeam : limit;
            }
            else
            {
                long reverseOffset = getResultsPerPage() - (teamCount % getResultsPerPage());
                reverseOffset = reverseOffset == 0 ? getResultsPerPage() : reverseOffset;
                offset = ((pageCount - (page)) * getResultsPerPage() * membersPerTeam) - (getResultsPerPage() - reverseOffset);
            }
        }

        MapSqlParameterSource params =
            ladderUtil.createSearchParams(season, regions, leagueTypes, queueType, teamType)
            .addValue("offset", offset)
            .addValue("limit", limit);

        boolean late = page > 5 || pageCount - page > 5;
        String q = late
            ? (reversed ? FIND_TEAM_MEMBERS_LATE_REVERSED_QUERY : FIND_TEAM_MEMBERS_LATE_QUERY)
            : (reversed ? FIND_TEAM_MEMBERS_REVERSED_QUERY : FIND_TEAM_MEMBERS_QUERY);
        List<LadderTeam> teams = template
            .query(q, params, LADDER_TEAM_EXTRACTOR);
        if(reversed) Collections.reverse(teams);

        return new PagedSearchResult<>(teamCount, (long) getResultsPerPage(), page, teams);
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
            ladderUtil.createSearchParams(season, regions, leagueTypes, queueType, teamType)
                .addValue("offset", offset)
                .addValue("limit", limit)
                .addValue("ratingAnchor", ratingAnchor)
                .addValue("idAnchor", idAnchor);

        String q = forward ? FIND_TEAM_MEMBERS_ANCHOR_QUERY : FIND_TEAM_MEMBERS_ANCHOR_REVERSED_QUERY;
        List<LadderTeam> teams = template
            .query(q, params, LADDER_TEAM_EXTRACTOR);
        if(!forward) Collections.reverse(teams);

        return new PagedSearchResult<>(null, (long) getResultsPerPage(), finalPage, teams);
    }

    public long getTeamCount
    (
        int season,
        Set<Region> regions,
        Set<League.LeagueType> leagueTypes,
        QueueType queueType,
        TeamType teamType
    )
    {
        MapSqlParameterSource params =
            ladderUtil.createSearchParams(season, regions, leagueTypes, queueType, teamType);
        return template.query(FIND_TEAM_COUNT_QUERY, params, DAOUtils.LONG_EXTRACTOR);
    }

    @Cacheable
    (
        cacheNames="search-seasons"
    )
    public List<Season> findSeasonList()
    {
        return seasonDAO.findListByFirstBattlenetId();
    }

    public List<LadderTeam> findCharacterTeams(long id)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("playerCharacterId", id);
        return template
            .query(FIND_CARACTER_TEAM_MEMBERS_QUERY, params, LADDER_TEAM_EXTRACTOR);
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
            ladderUtil.createSearchParams(season, regions, leagueTypes, queueType, teamType)
            .addValue("accountId", accountId);
        return template
            .query(FIND_FOLLOWING_TEAM_MEMBERS, params, LADDER_TEAM_EXTRACTOR);
    }

}
