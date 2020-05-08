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

import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.BaseLeague.LeagueType;
import com.nephest.battlenet.sc2.model.BaseLeagueTier.LeagueTierType;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.LeagueTier;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.dao.DAOUtils;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.ladder.*;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

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
        + "team.region AS \"team.region\", "
        + "team.league_type, team.queue_type, team.team_type, "
        + "team.tier_type, "
        + "team.id as \"team.id\", team.rating, team.wins, team.losses, "
        + "account.id AS \"account.id\", account.battle_tag,"
        + "player_character.id AS \"player_character.id\", "
        + "player_character.region AS \"player_character.region\", "
        + "player_character.battlenet_id AS \"player_character.battlenet_id\", player_character.realm, player_character.name, "
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
            + "OFFSET :offset LIMIT :limit"
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
        + "team.region AS \"team.region\", "
        + "team.league_type, team.queue_type, team.team_type, "
        + "team.tier_type, "
        + "team.id as \"team.id\", team.rating, team.wins, team.losses, "
        + "account.id AS \"account.id\", account.battle_tag,"
        + "player_character.id AS \"player_character.id\", "
        + "player_character.region AS \"player_character.region\", "
        + "player_character.battlenet_id AS \"player_character.battlenet_id\", player_character.realm, player_character.name, "
        + "team_member.terran_games_played, team_member.protoss_games_played, "
        + "team_member.zerg_games_played, team_member.random_games_played "

        + LADDER_SEARCH_TEAM_FROM_WHERE

        + "ORDER BY team.rating %1$s, team.id %1$s "
        + "OFFSET :offset LIMIT :limit";

    private static final String FIND_TEAM_MEMBERS_QUERY =
        String.format(FIND_TEAM_MEMBERS_FORMAT, "DESC");

    private static final String FIND_TEAM_MEMBERS_REVERSED_QUERY =
        String.format(FIND_TEAM_MEMBERS_FORMAT, "ASC");

    private static final String FIND_TEAM_MEMBERS_ANCHOR_FORMAT =
        "SELECT "
        + "team.region AS \"team.region\", "
        + "team.league_type, team.queue_type, team.team_type, "
        + "team.tier_type, "
        + "team.id as \"team.id\", team.rating, team.wins, team.losses, "
        + "account.id AS \"account.id\", account.battle_tag,"
        + "player_character.id AS \"player_character.id\", "
        + "player_character.region AS \"player_character.region\", "
        + "player_character.battlenet_id AS \"player_character.battlenet_id\", player_character.realm, player_character.name, "
        + "team_member.terran_games_played, team_member.protoss_games_played, "
        + "team_member.zerg_games_played, team_member.random_games_played "

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
        "SELECT "
        + "season.battlenet_id AS \"season.battlenet_id\", season.year, season.number, "
        + "team.region AS \"team.region\", team.league_type, team.queue_type, team.team_type, "
        + "team.tier_type, "
        + "team.id AS \"team.id\", team.rating, team.wins, team.losses, "
        + "account.id AS \"account.id\", account.battle_tag,"
        + "player_character.id AS \"player_character.id\", "
        + "player_character.region AS \"player_character.region\", "
        + "player_character.battlenet_id AS \"player_character.battlenet_id\", "
        + "player_character.realm, player_character.name, "
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
            + "player_character.id=:playerCharacterId"
        +") "
        + "ORDER BY team.season DESC, "
        + "team.queue_type ASC, team.team_type ASC, team.league_type DESC, "
        + "team.rating DESC, team.id ASC, "
        + "player_character.id ASC ";
    private static final String FIND_DISTINCT_CHARACTER_FORMAT =
        "SELECT "
        + "MAX(account.id) AS \"account.id\", "
        + "MAX(account.battle_tag) AS \"account.battle_tag\", "
        + "player_character_stats.player_character_id, "
        + "MAX(player_character.region) AS \"player_character.region\", "
        + "MAX(player_character.battlenet_id) AS \"player_character.battlenet_id\", "
        + "MAX(player_character.realm) AS \"player_character.realm\", "
        + "MAX(player_character.name) AS \"player_character.name\", "
        + "MAX(player_character_stats.race) AS \"race\", "
        + "MAX(player_character_stats.league_max) AS \"league_max\", "
        + "MAX(player_character_stats.rating_max) AS \"rating_max\", "
        + "MAX(player_character_stats.games_played) AS \"games_played\" "

        + "FROM player_character_stats "
        + "INNER JOIN "
        + "("
            + "SELECT MAX(player_character_stats.rating_max) AS rating_max_global "
            + "FROM player_character_stats "
            + "INNER JOIN player_character "
                + " ON player_character_stats.player_character_id=player_character.id "
            + "%2$s "
            + "AND COALESCE(player_character_stats.season_id, -32768) = -32768 "
            + "GROUP BY player_character_stats.player_character_id "
        + ") "
        + "player_character_stats_max ON player_character_stats.rating_max=player_character_stats_max.rating_max_global "
        + "INNER JOIN player_character ON player_character_stats.player_character_id=player_character.id "
        + "INNER JOIN account ON player_character.account_id = account.id "

        + "%1$s "
        + "AND COALESCE(player_character_stats.season_id, -32768) = -32768 "

        + "GROUP BY player_character_stats.player_character_id "

        + "ORDER BY rating_max DESC";
    private static final String FIND_DISTINCT_CHARACTER_BY_NAME_QUERY = String.format
    (
        FIND_DISTINCT_CHARACTER_FORMAT,
        "WHERE LOWER(player_character.name) LIKE LOWER(:name) ",
        "WHERE LOWER(player_character.name) LIKE LOWER(:name) "
    );
    private static final String FIND_DISTINCT_CHARACTER_BY_ACCOUNT_ID_QUERY = String.format
    (
        FIND_DISTINCT_CHARACTER_FORMAT,
        "WHERE account.id = :accountId ",
        "INNER JOIN account ON player_character.account_id=account.id "
        + "WHERE account.id = :accountId "
    );
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

    private static final String FIND_SEASON_LIST =
        "SELECT DISTINCT "
        + "battlenet_id, year, number "
        + "FROM season "
        + "ORDER BY battlenet_id DESC";

    private static final String FIND_LEAGUE_TIER_BOUNDS_QUERY =
        "SELECT "
        + "season.region, "
        + "league.type AS \"league.type\", "
        + "league_tier.type AS \"league_tier.type\", league_tier.min_rating, league_tier.max_rating "

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
    private SeasonDAO seasonDAO;

    private final ResultSetExtractor<List<LadderTeam>> LADDER_TEAM_EXTRACTOR
        = (rs)->{return mapTeams(rs, true);};
    private final ResultSetExtractor<List<LadderTeam>> LADDER_TEAM_SHORT_EXTRACTOR
        = (rs)->{return mapTeams(rs, false);};

    private final RowMapper<LadderDistinctCharacter> DISTINCT_CHARACTER_ROW_MAPPER =
    (rs, num)->
    {
        Integer gamesPlayed = rs.getInt("games_played");
        int raceInt = rs.getInt("race");
        Race race = rs.wasNull() ? null : conversionService.convert(raceInt, Race.class);
        return new LadderDistinctCharacter
        (
            conversionService.convert(rs.getInt("league_max"), League.LeagueType.class),
            rs.getInt("rating_max"),
            rs.getString("account.battle_tag"),
            new PlayerCharacter
            (
                rs.getLong("player_character_id"),
                rs.getLong("account.id"),
                conversionService.convert(rs.getInt("player_character.region"), Region.class),
                rs.getLong("player_character.battlenet_id"),
                rs.getInt("player_character.realm"),
                rs.getString("player_character.name")
            ),
            race == Race.TERRAN ? gamesPlayed : null,
            race == Race.PROTOSS ? gamesPlayed : null,
            race == Race.ZERG ? gamesPlayed : null,
            race == Race.RANDOM ? gamesPlayed : null,
            gamesPlayed
        );
    };

    private final ResultSetExtractor<Map<Region, Map<LeagueType, LadderSearchStatsResult>>> LADDER_STATS_EXTRACTOR =
    (rs)->
    {
        Map<Region, Map<LeagueType, LadderSearchStatsResult>> result = new EnumMap(Region.class);
        while(rs.next())
        {
            Region region = conversionService.convert(rs.getInt("region"), Region.class);
            LeagueType league = conversionService.convert(rs.getInt("type"), League.LeagueType.class);
            Long playerCount = rs.getLong("player_count");
            Long teamCount = rs.getLong("team_count");
            Map<Race, Long> gamesPlayed = new EnumMap(Race.class);
            for(Race race : Race.values())
                gamesPlayed.put(race, (long) Math.round(rs.getDouble(race.name().toLowerCase() + "_games_played")));
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
            Region region = conversionService.convert(rs.getInt("region"), Region.class);
            LeagueType league = conversionService.convert(rs.getInt("league.type"), LeagueType.class);
            LeagueTierType tier = conversionService.convert(rs.getInt("league_tier.type"), LeagueTierType.class);
            Integer minRating = rs.getInt("min_rating");
            Integer maxRating = rs.getInt("max_rating");

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
                    rs.getInt("year"),
                    rs.getInt("number")
                );
                LadderTeam team = new LadderTeam
                (
                    teamId,
                    season,
                    conversionService.convert(rs.getInt("team.region"), Region.class),
                    new BaseLeague
                    (
                        conversionService.convert(rs.getInt("league_type"), League.LeagueType.class),
                        conversionService.convert(rs.getInt("queue_type"), QueueType.class),
                        conversionService.convert(rs.getInt("team_type"), TeamType.class)
                    ),
                    conversionService.convert(rs.getInt("tier_type"), LeagueTier.LeagueTierType.class),
                    members,
                    rs.getLong("rating"),
                    rs.getInt("wins"), rs.getInt("losses"), null,
                    null
                );
                teams.add(team);
                lastTeamId = teamId;
            }

            LadderTeamMember member = new LadderTeamMember
            (
                rs.getString("battle_tag"),
                new PlayerCharacter
                (
                    rs.getLong("player_character.id"),
                    rs.getLong("account.id"),
                    conversionService.convert(rs.getInt("player_character.region"), Region.class),
                    rs.getLong("player_character.battlenet_id"),
                    rs.getInt("realm"),
                    rs.getString("name")
                ),
                (Integer) rs.getObject("terran_games_played"),
                (Integer) rs.getObject("protoss_games_played"),
                (Integer) rs.getObject("zerg_games_played"),
                (Integer) rs.getObject("random_games_played")
            );
            members.add(member);
        }
        return teams;
    };

    @Cacheable
    (
        cacheNames="search-ladder",
        condition="#a5 eq 1 and #a0 eq #root.target.seasonDAO.maxBattlenetId"
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
        long teamCount = ladderSearchDAO.getTeamCount(season, regions, leagueTypes, queueType, teamType);
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

    public PagedSearchResult<List<LadderTeam>> findAnchored
    (
        long season,
        Set<Region> regions,
        Set<League.LeagueType> leagueTypes,
        QueueType queueType,
        TeamType teamType,
        long page,
        long ratingAnchor,
        long idAnchor,
        boolean forward,
        int count
    )
    {
        long finalPage = forward ? page + count : page - count;
        long teamCount = ladderSearchDAO.getTeamCount(season, regions, leagueTypes, queueType, teamType);
        long pageCount = (long) Math.ceil(teamCount /(double) getResultsPerPage());
        long membersPerTeam = getMemberCount(queueType, teamType);
        long offset = (count - 1) * getResultsPerPage() * membersPerTeam;
        long limit = getResultsPerPage() * membersPerTeam;
        //if last page is requested, show only leftovers
        if(page == pageCount + 1)
        {
            limit = (teamCount % getResultsPerPage()) * membersPerTeam;
            limit = limit == 0 ? getResultsPerPage() * membersPerTeam : limit;
        }
        MapSqlParameterSource params =
            createSearchParams(season, regions, leagueTypes, queueType, teamType)
                .addValue("offset", offset)
                .addValue("limit", limit)
                .addValue("ratingAnchor", ratingAnchor)
                .addValue("idAnchor", idAnchor);

        String q = forward ? FIND_TEAM_MEMBERS_ANCHOR_QUERY : FIND_TEAM_MEMBERS_ANCHOR_REVERSED_QUERY;
        List<LadderTeam> teams = template
            .query(q, params, LADDER_TEAM_SHORT_EXTRACTOR);
        if(!forward) Collections.reverse(teams);
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
            finalPage,
            teams
        );
    }

    @Cacheable(cacheNames = "search-team-count")
    public long getTeamCount
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
        return template.query(FIND_TEAM_COUNT_QUERY, params, DAOUtils.LONG_EXTRACTOR);
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
        condition="#a0 eq #root.target.seasonDAO.maxBattlenetId"
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
        condition="#a0 eq #root.target.seasonDAO.maxBattlenetId"
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
        condition="#a0 eq #root.target.seasonDAO.maxBattlenetId"
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

    public List<LadderTeam> findCharacterTeams(long id)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("playerCharacterId", id);
        return template
            .query(FIND_CARACTER_TEAM_MEMBERS_QUERY, params, LADDER_TEAM_EXTRACTOR);
    }

    public List<LadderDistinctCharacter> findDistinctCharactersByName(String name)
    {
        name = PostgreSQLUtils.escapeLikePattern(name) + "#%";
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("name", name);
        return template
            .query(FIND_DISTINCT_CHARACTER_BY_NAME_QUERY, params, DISTINCT_CHARACTER_ROW_MAPPER);
    }

    public List<LadderDistinctCharacter> findDistinctCharactersByAccountId(Long accountId)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("accountId", accountId);
        return template
            .query(FIND_DISTINCT_CHARACTER_BY_ACCOUNT_ID_QUERY, params, DISTINCT_CHARACTER_ROW_MAPPER);
    }

}
