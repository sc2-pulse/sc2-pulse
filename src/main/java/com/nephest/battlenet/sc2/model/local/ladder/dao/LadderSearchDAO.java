/*-
 * =========================LICENSE_START=========================
 * SC2 Ladder Generator
 * %%
 * Copyright (C) 2020 Oleksandr Masniuk
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END=========================
 */
package com.nephest.battlenet.sc2.model.local.ladder.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeague.LeagueType;
import com.nephest.battlenet.sc2.model.BaseLeagueTier.LeagueTierType;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.LeagueTier;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderSearchStatsResult;
import com.nephest.battlenet.sc2.model.local.ladder.LadderSeason;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.model.local.ladder.MergedLadderSearchStatsResult;
import com.nephest.battlenet.sc2.model.local.ladder.PagedSearchResult;

@Repository
public class LadderSearchDAO
{

    private static final Map<String, Object> DEFAULT_TEAM_MEMBER_QUERY_VALUES;

    static
    {
        Map<String, Object> vals = new HashMap();
        vals.put("region0", null);
        vals.put("region1", null);
        vals.put("region2", null);
        vals.put("region3", null);
        vals.put("leagueType0", null);
        vals.put("leagueType1", null);
        vals.put("leagueType2", null);
        vals.put("leagueType3", null);
        vals.put("leagueType4", null);
        vals.put("leagueType5", null);
        vals.put("leagueType6", null);
        DEFAULT_TEAM_MEMBER_QUERY_VALUES = Collections.unmodifiableMap(vals);
    }

    private static final String LADDER_SEARCH_TEAM_FROM =
        "FROM team_member "
        + "INNER JOIN team ON team_member.team_id=team.id "
        + "INNER JOIN player_character ON team_member.player_character_id=player_character.id "
        + "INNER JOIN account ON player_character.account_id=account.id ";

    private static final String LADDER_SEARCH_TEAM_WHERE =
        "WHERE "
        + "team.season=:seasonId "
        + "AND team.region IN (:region0, :region1, :region2, :region3) "
        + "AND team.league_type IN (:leagueType0, :leagueType1, :leagueType2, :leagueType3, :leagueType4, :leagueType5, :leagueType6) "
        + "AND team.queue_type=:queueType "
        + "AND team.team_type=:teamType ";

    private static final String LADDER_SEARCH_WHERE =
        "WHERE "
        + "season.battlenet_id=:seasonId "
        + "AND season.region IN (:region0, :region1, :region2, :region3) "
        + "AND league.type IN (:leagueType0, :leagueType1, :leagueType2, :leagueType3, :leagueType4, :leagueType5, :leagueType6) "
        + "AND league.queue_type=:queueType "
        + "AND league.team_type=:teamType ";



    private static final String LADDER_SEARCH_TEAM_FROM_WHERE =
        LADDER_SEARCH_TEAM_FROM + LADDER_SEARCH_TEAM_WHERE;

    private static final String FIND_TEAM_MEMBERS_LATE_FORMAT =
        "SELECT "
        + "team.region, "
        + "team.league_type, team.queue_type, team.team_type, "
        + "team.tier_type, "
        + "team.id, team.rating, team.wins, team.losses, "
        + "account.battle_tag, "
        + "player_character.battlenet_id, player_character.realm, player_character.name, "
        + "team_member.terran_games_played, team_member.protoss_games_played, "
        + "team_member.zerg_games_played, team_member.random_games_played "

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
            + "LIMIT :offset, :limit"
        + ") o "
        + "JOIN team_member ON team_member.team_id=o.team_id AND team_member.player_character_id=o.player_character_id "
        + "INNER JOIN team ON team_member.team_id=team.id "
        + "INNER JOIN player_character ON team_member.player_character_id=player_character.id "
        + "INNER JOIN account ON player_character.account_id=account.id "
        + "ORDER BY team.rating %1$s, team.id %1$s ";

    private static final String FIND_TEAM_MEMBERS_LATE_QUERY =
        String.format(FIND_TEAM_MEMBERS_LATE_FORMAT, "DESC");

    private static final String FIND_TEAM_MEMBERS_LATE_REVERSED_QUERY =
        String.format(FIND_TEAM_MEMBERS_LATE_FORMAT, "ASC");

    private static final String FIND_TEAM_MEMBERS_FORMAT =
        "SELECT "
        + "team.region, "
        + "team.league_type, team.queue_type, team.team_type, "
        + "team.tier_type, "
        + "team.id, team.rating, team.wins, team.losses, "
        + "account.battle_tag, "
        + "player_character.battlenet_id, player_character.realm, player_character.name, "
        + "team_member.terran_games_played, team_member.protoss_games_played, "
        + "team_member.zerg_games_played, team_member.random_games_played "

        + LADDER_SEARCH_TEAM_FROM_WHERE

        + "ORDER BY team.rating %1$s, team.id %1$s "
        + "LIMIT :offset, :limit";

    private static final String FIND_TEAM_MEMBERS_QUERY =
        String.format(FIND_TEAM_MEMBERS_LATE_FORMAT, "DESC");

    private static final String FIND_TEAM_MEMBERS_REVERSED_QUERY =
        String.format(FIND_TEAM_MEMBERS_LATE_FORMAT, "ASC");

    private static final String FIND_TEAM_MEMBERS_COUNT_QUERY =
        "SELECT COUNT(*) "
        + "FROM team_member "
        + "INNER JOIN team ON team_member.team_id=team.id "
        + LADDER_SEARCH_TEAM_WHERE;

    private static final String FIND_CARACTER_TEAM_MEMBERS_QUERY =
        "SELECT "
        + "season.battlenet_id, season.year, season.number, "
        + "team.region, team.league_type, team.queue_type, team.team_type, "
        + "team.tier_type, "
        + "team.id, team.rating, team.wins, team.losses, "
        + "account.battle_tag, "
        + "player_character.battlenet_id, player_character.realm, player_character.name, "
        + "team_member.terran_games_played, team_member.protoss_games_played, "
        + "team_member.zerg_games_played, team_member.random_games_played "

        + LADDER_SEARCH_TEAM_FROM
        + "INNER JOIN season ON season.battlenet_id=team.season AND season.region=team.region "

        + "WHERE team.id IN"
        + "("
            + "SELECT "
            + "team.id "
            + "FROM team "
            + "INNER JOIN team_member ON team_member.team_id=team.id "
            + "INNER JOIN player_character ON team_member.player_character_id=player_character.id "
            + "WHERE "
            + "team.region=:region "
            + "AND player_character.battlenet_id=:battlenetId"
        +") "
        + "ORDER BY team.season DESC, "
        + "team.queue_type ASC, team.team_type ASC, team.league_type DESC, "
        + "team.rating DESC, team.id ASC, "
        + "player_character.id ASC ";
    private static final String FIND_DISTINCT_CHARACTER_QUERY =
        "SELECT "
        + "MAX(team.region) as region_max, "
        + "MAX(team.league_type) as league_max, "
        + "MAX(team.rating) as rating_max, "
        + "MAX(account.battle_tag) as battle_tag_concat, "
        + "MAX(player_character.battlenet_id) as battlenet_id_max, "
        + "MAX(player_character.realm) as realm_max, "
        + "MAX(player_character.name) as name_concat, "
        + "SUM(team_member.terran_games_played) as games_terran, "
        + "SUM(team_member.protoss_games_played) as games_protoss, "
        + "SUM(team_member.zerg_games_played) as games_zerg, "
        + "SUM(team_member.random_games_played) as games_random "

        + LADDER_SEARCH_TEAM_FROM

        + "WHERE player_character.id IN (SELECT player_character.id from player_character WHERE player_character.name LIKE :name) "

        + "GROUP BY player_character.id "
        + "ORDER BY rating_max DESC";
    private static final String LADDER_SEARCH_STATS_QUERY =
        "SELECT "
        + "season.region, "
        + "league.type, "
        + "league_stats.player_count, "
        + "league_stats.team_count, "
        + "league_stats.terran_games_played, "
        + "league_stats.protoss_games_played, "
        + "league_stats.zerg_games_played, "
        + "league_stats.random_games_played "


        + "FROM league_stats "
        + "INNER JOIN league ON league_stats.league_id=league.id "
        + "INNER JOIN season ON league.season_id = season.id "

        + "WHERE "
        + "season.battlenet_id=:seasonId "
        + "AND season.region IN (:region0, :region1, :region2, :region3) "
        + "AND league.type IN (:leagueType0, :leagueType1, :leagueType2, :leagueType3, :leagueType4, :leagueType5, :leagueType6) "
        + "AND league.queue_type=:queueType "
        + "AND league.team_type=:teamType ";

    private static final String FIND_LAST_SEASON_ID_QUERY =
        "SELECT MAX(battlenet_id) FROM season";

    private static final String FIND_SEASON_LIST =
        "SELECT DISTINCT "
        + "battlenet_id, year, number "
        + "FROM season "
        + "ORDER BY battlenet_id DESC";

    private static final String FIND_LEAGUE_TIER_BOUNDS_QUERY =
        "SELECT "
        + "season.region, "
        + "league.type, "
        + "league_tier.type, league_tier.min_rating, league_tier.max_rating "

        + "FROM league_tier "
        + "INNER JOIN league ON league_tier.league_id=league.id "
        + "INNER JOIN season ON league.season_id=season.id "

        + LADDER_SEARCH_WHERE;

    private static final String FIND_SEASON_META_LIST_QUERY =
        "SELECT "
        + "region, battlenet_id, year, number "
        + "FROM season "
        + "WHERE season.battlenet_id=:seasonId "
        + "AND season.region IN (:region0, :region1, :region2, :region3)";


    private static final String LAST_SELECTED_ROWS =
        "SELECT FOUND_ROWS()";

    private NamedParameterJdbcTemplate template;
    private ConversionService conversionService;

    private final ResultSetExtractor<List<LadderTeam>> LADDER_TEAM_EXTRACTOR
        = (rs)->{return mapTeams(rs, true);};
    private final ResultSetExtractor<List<LadderTeam>> LADDER_TEAM_SHORT_EXTRACTOR
        = (rs)->{return mapTeams(rs, false);};

    private final RowMapper<LadderDistinctCharacter> DISTINCT_CHARACTER_ROW_MAPPER =
    (rs, num)->
    {
        return new LadderDistinctCharacter
        (
            conversionService.convert(rs.getInt("region_max"), Region.class),
            conversionService.convert(rs.getInt("league_max"), League.LeagueType.class),
            rs.getInt("rating_max"),
            rs.getLong("battlenet_id_max"),
            rs.getInt("realm_max"),
            rs.getString("battle_tag_concat"),
            rs.getString("name_concat"),
            rs.getInt("games_terran"),
            rs.getInt("games_protoss"),
            rs.getInt("games_zerg"),
            rs.getInt("games_random")
        );
    };

    private final ResultSetExtractor<Map<Region, Map<LeagueType, LadderSearchStatsResult>>> LADDER_STATS_EXTRACTOR =
    (rs)->
    {
        Map<Region, Map<LeagueType, LadderSearchStatsResult>> result = new EnumMap(Region.class);
        while(rs.next())
        {
            Region region = conversionService.convert(rs.getInt("season.region"), Region.class);
            LeagueType league = conversionService.convert(rs.getInt("league.type"), League.LeagueType.class);
            Long playerCount = rs.getLong("league_stats.player_count");
            Long teamCount = rs.getLong("league_stats.team_count");
            Map<Race, Long> gamesPlayed = new EnumMap(Race.class);
            for(Race race : Race.values())
                gamesPlayed.put(race, (long) Math.round(rs.getDouble("league_stats." + race.name().toLowerCase() + "_games_played")));
            Map<LeagueType, LadderSearchStatsResult> leagueResults =
                result.computeIfAbsent(region, (reg)->new EnumMap(LeagueType.class));
            leagueResults.put(league, new LadderSearchStatsResult(playerCount, teamCount, gamesPlayed));
        }
        return result;
    };

    private final ResultSetExtractor<Map<Region, Map<LeagueType, Map<LeagueTierType, Integer[]>>>> LEAGUE_TIER_BOUNDS_EXTRACTOR =
    (rs)->
    {
        Map<Region, Map<LeagueType, Map<LeagueTierType, Integer[]>>> result = new EnumMap(Region.class);
        while(rs.next())
        {
            Region region = conversionService.convert(rs.getInt("season.region"), Region.class);
            LeagueType league = conversionService.convert(rs.getInt("league.type"), LeagueType.class);
            LeagueTierType tier = conversionService.convert(rs.getInt("league_tier.type"), LeagueTierType.class);
            Integer minRating = rs.getInt("league_tier.min_rating");
            Integer maxRating = rs.getInt("league_tier.max_rating");

            Map<LeagueType, Map<LeagueTierType, Integer[]>> leagues =
                result.computeIfAbsent(region, (reg)->new EnumMap(LeagueType.class));
            Map<LeagueTierType, Integer[]> tiers =
                leagues.computeIfAbsent(league, (lea)->new EnumMap(LeagueTierType.class));
            Integer[] bounds =
                tiers.computeIfAbsent(tier, (tie)->new Integer[2]);
            bounds[0] = minRating;
            bounds[1] = maxRating;
        }
        return result;
    };

    private static final RowMapper<LadderSeason> FIND_SEASON_LIST_ROW_MAPPER =
    (rs, num)->
    {
        return new LadderSeason
        (
            rs.getLong("battlenet_id"),
            rs.getInt("year"),
            rs.getInt("number")
        );
    };

    private final RowMapper<Season> SEASON_META_LIST_ROW_MAPPER =
    (rs, num)->
    {
        return new Season
        (
            null,
            rs.getLong("battlenet_id"),
            conversionService.convert(rs.getInt("region"), Region.class),
            rs.getInt("year"),
            rs.getInt("number")
        );
    };

    private static final ResultSetExtractor<Long> LONG_EXTRACTOR =
    (rs)->
    {
        rs.next();
        return rs.getLong(1);
    };

    private int resultsPerPage = 100;

    LadderSearchDAO(){}

    @Autowired
    public LadderSearchDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
    }

    protected void setResultsPerPage(int resultsPerPage)
    {
        this.resultsPerPage = resultsPerPage;
    }
    
    public int getResultsPerPage()
    {
        return resultsPerPage;
    }

    private List<LadderTeam> mapTeams(ResultSet rs, boolean includeSeason)
    throws SQLException
    {
        long lastTeamId = -1;
        List<LadderTeam> teams = new ArrayList();
        List<LadderTeamMember> members = null;
        while(rs.next())
        {
           // if(totalCount == 0) totalCount = rs.getLong("total_team_count");
            long teamId = rs.getLong("team.id");
            if (teamId != lastTeamId)
            {
                members = new ArrayList();
                LadderSeason season = !includeSeason ? null : new LadderSeason
                (
                    rs.getLong("season.battlenet_id"),
                    rs.getInt("season.year"),
                    rs.getInt("season.number")
                );
                LadderTeam team = new LadderTeam
                (
                    season,
                    conversionService.convert(rs.getInt("team.region"), Region.class),
                    new BaseLeague
                    (
                        conversionService.convert(rs.getInt("team.league_type"), League.LeagueType.class),
                        conversionService.convert(rs.getInt("team.queue_type"), QueueType.class),
                        conversionService.convert(rs.getInt("team.team_type"), TeamType.class)
                    ),
                    conversionService.convert(rs.getInt("team.tier_type"), LeagueTier.LeagueTierType.class),
                    members,
                    rs.getLong("team.rating"),
                    rs.getInt("team.wins"), rs.getInt("team.losses"), null,
                    null
                );
                teams.add(team);
                lastTeamId = teamId;
            }

            LadderTeamMember member = new LadderTeamMember
            (
                rs.getLong("player_character.battlenet_id"),
                rs.getInt("player_character.realm"),
                rs.getString("account.battle_tag"),
                rs.getString("player_character.name"),
                (Integer) rs.getObject("team_member.terran_games_played"),
                (Integer) rs.getObject("team_member.protoss_games_played"),
                (Integer) rs.getObject("team_member.zerg_games_played"),
                (Integer) rs.getObject("team_member.random_games_played")
            );
            members.add(member);
        }
        return teams;
    };

    @Cacheable
    (
        cacheNames="search-season-last"
    )
    public long getLastSeasonId()
    {
        return template
            .query(FIND_LAST_SEASON_ID_QUERY, LONG_EXTRACTOR);
    }

    @Cacheable
    (
        cacheNames="search-ladder",
        condition="#a5 eq 1 and #a0 eq #root.target.lastSeasonId"
    )
    public PagedSearchResult<List<LadderTeam>> find
    (
        long season,
        Set<Region> regions,
        Set<League.LeagueType> leagueTypes,
        QueueType queueType,
        TeamType teamType,
        long page
    )
    {
        long membersPerTeam = getMemberCount(queueType, teamType);
        long teamCount = (long) Math.ceil(getMemberCount(season, regions, leagueTypes, queueType, teamType) / (double) membersPerTeam);
        long pageCount = (long) Math.ceil(teamCount /(double) getResultsPerPage());
        long middlePage = (long) Math.ceil(pageCount / 2d);
        long offset = 0;
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
            createSearchParams(season, regions, leagueTypes, queueType, teamType)
            .addValue("offset", offset)
            .addValue("limit", limit);

        boolean late = page > 5 || pageCount - page > 5;
        String q = late
            ? (reversed ? FIND_TEAM_MEMBERS_LATE_REVERSED_QUERY : FIND_TEAM_MEMBERS_LATE_QUERY)
            : (reversed ? FIND_TEAM_MEMBERS_REVERSED_QUERY : FIND_TEAM_MEMBERS_QUERY);
        List<LadderTeam> teams = template
            .query(q, params, LADDER_TEAM_SHORT_EXTRACTOR);
        if(reversed) Collections.reverse(teams);
        /*
            Blizzard sometimes returns invalid team members and they are ignored
            by the corresponding service. It is very rare, but in such occasion
            it is possible to have non full team(3 members out of 4, etc.) to be fetched.
            It can lead to fetching some strain team members into the result set.
            Ignoring such team members to return consistent results.
        */
        if(teams.size() > getResultsPerPage()) teams = teams.subList(0, getResultsPerPage());

        return new PagedSearchResult<List<LadderTeam>>
        (
            teamCount,
            (long) getResultsPerPage(),
            page,
            teams
        );
    }

    private long getMemberCount
    (
        long season,
        Set<Region> regions,
        Set<League.LeagueType> leagueTypes,
        QueueType queueType,
        TeamType teamType
    )
    {
        MapSqlParameterSource params =
            createSearchParams(season, regions, leagueTypes, queueType, teamType);
        return template.query(FIND_TEAM_MEMBERS_COUNT_QUERY, params, LONG_EXTRACTOR);
    }

    @Cacheable
    (
        cacheNames="search-seasons"
    )
    public List<LadderSeason> findSeasonList()
    {
        return template.query
        (
            FIND_SEASON_LIST,
            FIND_SEASON_LIST_ROW_MAPPER
        );
    }

    private static final int getMemberCount(QueueType queueType, TeamType teamType)
    {
        if(teamType == TeamType.RANDOM) return 1;
        else return queueType.getTeamFormat().getMemberCount();
    }

    private MapSqlParameterSource createSearchParams
    (
        long season,
        Set<Region> regions,
        Set<League.LeagueType> leagueTypes,
        QueueType queueType,
        TeamType teamType
    )
    {
        MapSqlParameterSource params = new MapSqlParameterSource(DEFAULT_TEAM_MEMBER_QUERY_VALUES)
            .addValue("seasonId", season)
            .addValue("queueType", conversionService.convert(queueType, Integer.class))
            .addValue("teamType", conversionService.convert(teamType, Integer.class));

        int i = 0;
        for(Region region : regions)
        {
            params.addValue("region" + i, conversionService.convert(region, Integer.class));
            i++;
        }

        i = 0;
        for(League.LeagueType leagueType : leagueTypes)
        {
            params.addValue("leagueType" + i, conversionService.convert(leagueType, Integer.class));
            i++;
        }

        return params;
    }

    @Cacheable
    (
        cacheNames="search-ladder-stats",
        condition="#a0 eq #root.target.lastSeasonId"
    )
    public MergedLadderSearchStatsResult findStats
    (
        long season,
        Set<Region> regions,
        Set<League.LeagueType> leagueTypes,
        QueueType queueType,
        TeamType teamType
    )
    {
        MapSqlParameterSource params =
            createSearchParams(season, regions, leagueTypes, queueType, teamType)
            //2 teams play the same game, all members of a team play the same game
            .addValue("memberCoeff", queueType.getTeamFormat().getMemberCount() * 2);
        Map<Region, Map<LeagueType, LadderSearchStatsResult>> stats = template
            .query(LADDER_SEARCH_STATS_QUERY, params, LADDER_STATS_EXTRACTOR);

        return new MergedLadderSearchStatsResult(stats);
    }

    @Cacheable
    (
        cacheNames="search-ladder-league-bounds",
        condition="#a0 eq #root.target.lastSeasonId"
    )
    public Map<Region, Map<LeagueType, Map<LeagueTierType, Integer[]>>> findLeagueBounds
    (
        long season,
        Set<Region> regions,
        Set<League.LeagueType> leagueTypes,
        QueueType queueType,
        TeamType teamType
    )
    {
        MapSqlParameterSource params =
            createSearchParams(season, regions, leagueTypes, queueType, teamType);
        return template
            .query(FIND_LEAGUE_TIER_BOUNDS_QUERY, params, LEAGUE_TIER_BOUNDS_EXTRACTOR);
    }

    @Cacheable
    (
        cacheNames="search-ladder-season",
        condition="#a0 eq #root.target.lastSeasonId"
    )
    public List<Season> findSeasonsMeta
    (
        long season,
        Set<Region> regions,
        Set<League.LeagueType> leagueTypes,
        QueueType queueType,
        TeamType teamType
    )
    {
        MapSqlParameterSource params =
            createSearchParams(season, regions, leagueTypes, queueType, teamType);
        return template
            .query(FIND_SEASON_META_LIST_QUERY, params, SEASON_META_LIST_ROW_MAPPER);
    }

    public List<LadderTeam> findCharacterTeams
    (
        Region region,
        long battlenetId
    )
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("region", conversionService.convert(region, Integer.class))
            .addValue("battlenetId", battlenetId);
        return template
            .query(FIND_CARACTER_TEAM_MEMBERS_QUERY, params, LADDER_TEAM_EXTRACTOR);
    }

    public List<LadderDistinctCharacter> findDistinctCharacters
    (
        String name
    )
    {
        name += "#%";
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("name", name);
        return template
            .query(FIND_DISTINCT_CHARACTER_QUERY, params, DISTINCT_CHARACTER_ROW_MAPPER);
    }

}
