// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.ladder.PagedSearchResult;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Repository
public class ClanDAO
{

    private static final Logger LOG = LoggerFactory.getLogger(ClanDAO.class);

    public static final String STD_SELECT_SHORT =
        "clan.id AS \"clan.id\", "
        + "clan.tag AS \"clan.tag\", "
        + "clan.region AS \"clan.region\", "
        + "clan.name AS \"clan.name\" ";
    public static final String STD_SELECT_SHORT_NULL =
        STD_SELECT_SHORT + ", "
        + "null::smallint AS \"clan.members\", "
        + "null::smallint AS \"clan.active_members\", "
        + "null::smallint AS \"clan.avg_rating\", "
        + "null::smallint AS \"clan.avg_league_type\", "
        + "null::integer AS \"clan.games\" ";
    public static final String STD_SELECT =
        STD_SELECT_SHORT + ", "
        + "clan.members AS \"clan.members\", "
        + "clan.active_members AS \"clan.active_members\", "
        + "clan.avg_rating AS \"clan.avg_rating\", "
        + "clan.avg_league_type AS \"clan.avg_league_type\", "
        + "clan.games AS \"clan.games\" ";

    private static final String MERGE_QUERY =
        "WITH "
        + "vals AS (VALUES :clans), "
        + "existing AS "
        + "("
            + "SELECT " + STD_SELECT_SHORT_NULL
            + "FROM vals v(tag, region, name) "
            + "INNER JOIN clan USING(tag, region)"
        + "), "
        + "updated AS "
        + "("
            + "UPDATE clan "
            + "SET name = COALESCE(v.name, clan.name) "
            + "FROM vals v (tag, region, name) "
            + "WHERE clan.tag = v.tag "
            + "AND clan.region = v.region "
            + "AND v.name IS NOT NULL "
            + "AND clan.name != v.name "
        + "), "
        + "missing AS "
        + "("
            + "SELECT v.tag, v.region, v.name "
            + "FROM vals v (tag, region, name) "
            + "LEFT JOIN existing ON v.tag = existing.\"clan.tag\" AND v.region = existing.\"clan.region\" "
            + "WHERE existing.\"clan.id\" IS NULL "
        + "), "
        + "inserted AS "
        + "("
            + "INSERT INTO clan (tag, region, name) "
            + "SELECT * FROM missing "
            + "ON CONFLICT(tag, region) DO UPDATE "
            + "SET name = COALESCE(excluded.name, clan.name) "
            + "RETURNING " + STD_SELECT_SHORT_NULL
        + ") "
        + "SELECT * FROM existing "
        + "UNION "
        + "SELECT * FROM inserted";

    private static final String FIND_IDS_BY_ID_CURSOR =
        "SELECT id "
        + "FROM clan "
        + "WHERE id > :cursor "
        + "ORDER BY id ASC "
        + "LIMIT :limit";

    private static final String FIND_BY_MIN_MEMBER_COUNT =
        "SELECT clan_id "
        + "FROM player_character "
        + "GROUP BY clan_id "
        + "HAVING COUNT(*) >= :minMemberCount";

    private static final String FIND_BY_IDS = "SELECT " + STD_SELECT + "FROM clan WHERE id IN(:ids)";
    private static final String FIND_BY_TAG = "SELECT " + STD_SELECT + "FROM clan WHERE tag = :tag";
    private static final String FIND_BY_TAG_OR_NAME = "SELECT " + STD_SELECT
        + "FROM clan "
        + "WHERE tag = :tag OR LOWER(name) LIKE LOWER(:nameLike) "
        + "ORDER BY active_members DESC NULLS LAST";

    private static final String FIND_BY_CURSOR_TEMPLATE =
        "SELECT " + STD_SELECT
        + "FROM clan "
        + "WHERE %1$s "
        + "AND active_members BETWEEN :minActiveMembers AND :maxActiveMembers "
        + "AND games / active_members BETWEEN :minGamesPerActiveMember AND :maxGamesPerActiveMember "
        + "AND avg_rating BETWEEN :minAvgRating AND :maxAvgRating "
        + "ORDER BY %2$s "
        + "LIMIT :limit OFFSET :offset";

    private static final String FIND_BY_ACTIVE_MEMBERS_CURSOR_TEMPLATE =
        String.format(FIND_BY_CURSOR_TEMPLATE,
            "(active_members, id) %2$s (:cursor, :idCursor)",
            "active_members %1$s, id %1$s");
    private static final String FIND_BY_ACTIVE_MEMBERS_CURSOR =
        String.format(FIND_BY_ACTIVE_MEMBERS_CURSOR_TEMPLATE, "DESC", "<");
    private static final String FIND_BY_ACTIVE_MEMBERS_CURSOR_REVERSED =
        String.format(FIND_BY_ACTIVE_MEMBERS_CURSOR_TEMPLATE, "ASC", ">");

    private static final String FIND_BY_GAMES_PER_ACTIVE_MEMBER_CURSOR_TEMPLATE =
        String.format(FIND_BY_CURSOR_TEMPLATE,
            "(games / active_members, id) %2$s (:cursor, :idCursor)",
            "games / active_members %1$s, id %1$s");
    private static final String FIND_BY_GAMES_PER_ACTIVE_MEMBER_CURSOR =
        String.format(FIND_BY_GAMES_PER_ACTIVE_MEMBER_CURSOR_TEMPLATE, "DESC", "<");
    private static final String FIND_BY_GAMES_PER_ACTIVE_MEMBER_REVERSED =
        String.format(FIND_BY_GAMES_PER_ACTIVE_MEMBER_CURSOR_TEMPLATE, "ASC", ">");

    private static final String FIND_BY_AVG_RATING_CURSOR_TEMPLATE =
        String.format(FIND_BY_CURSOR_TEMPLATE,
            "(avg_rating, id) %2$s (:cursor, :idCursor)",
            "avg_rating %1$s, id %1$s");
    private static final String FIND_BY_AVG_RATING_CURSOR =
        String.format(FIND_BY_AVG_RATING_CURSOR_TEMPLATE, "DESC", "<");
    private static final String FIND_BY_AVG_RATING_CURSOR_REVERSED =
        String.format(FIND_BY_AVG_RATING_CURSOR_TEMPLATE, "ASC", ">");

    private static final String FIND_BY_MEMBERS_TEMPLATE =
        String.format(FIND_BY_CURSOR_TEMPLATE,
            "(members, id) %2$s (:cursor, :idCursor)",
            "members %1$s, id %1$s");
    private static final String FIND_BY_MEMBERS_CURSOR =
        String.format(FIND_BY_MEMBERS_TEMPLATE, "DESC", "<");
    private static final String FIND_BY_MEMBERS_CURSOR_REVERSED =
        String.format(FIND_BY_MEMBERS_TEMPLATE, "ASC", ">");

    private static final String UPDATE_STATS = "WITH "
        + "character_filter AS (SELECT id FROM player_character WHERE clan_id IN (:clans)), "
        + "all_unwrap AS "
        + "("
            + "SELECT * FROM get_player_character_summary"
            + "(ARRAY(SELECT * FROM character_filter), :from, :races::smallint[]) player_character_summary"
        + "), "
        + "clan_stats AS "
        + "("
            + "SELECT clan_id, "
            + "COUNT(DISTINCT(player_character_id)) AS active_members, "
            + "AVG(all_unwrap.rating_avg)::smallint AS avg_rating, "
            + "AVG(all_unwrap.league_type_last)::smallint AS avg_league_type, "
            + "SUM(all_unwrap.games) AS games "
            + "FROM all_unwrap "
            + "INNER JOIN player_character ON all_unwrap.player_character_id = player_character.id "
            + "GROUP BY clan_id"
        + "), "
        + "members AS "
        + "("
            + "SELECT clan_id, COUNT(*) as count "
            + "FROM player_character "
            + "WHERE clan_id IN (:clans) "
            + "GROUP BY clan_id"
        + ") "
        + "UPDATE clan "
        + "SET members = members.count, "
        + "active_members = clan_stats.active_members, "
        + "avg_rating = clan_stats.avg_rating, "
        + "avg_league_type = clan_stats.avg_league_type, "
        + "games = clan_stats.games "
        + "FROM members "
        + "LEFT JOIN clan_stats USING(clan_id) "
        + "WHERE clan.id = members.clan_id "
        + "AND clan.members IS DISTINCT FROM members.count "
        + "AND clan.active_members IS DISTINCT FROM clan_stats.active_members "
        + "AND clan.avg_rating IS DISTINCT FROM clan_stats.avg_rating "
        + "AND clan.avg_league_type IS DISTINCT FROM clan_stats.avg_league_type "
        + "AND clan.games IS DISTINCT FROM clan_stats.games";

    private static RowMapper<Clan> STD_ROW_MAPPER;
    private static ResultSetExtractor<Clan> STD_EXTRACTOR;

    public static final int CLAN_STATS_BATCH_SIZE = 12;
    public static final int CLAN_STATS_DEPTH_DAYS = 60;
    public static final int CLAN_STATS_MIN_MEMBERS = 4;

    public static final int PAGE_SIZE = 50;
    public static final int MAX_PAGE_DIFF = 2;

    public static final int NAME_LIKE_MIN_LENGTH = 3;

    public enum Cursor
    {
        MEMBERS
        (
            "Total members",
            false,
            FIND_BY_MEMBERS_CURSOR,
            FIND_BY_MEMBERS_CURSOR_REVERSED
        ),
        ACTIVE_MEMBERS
        (
            "Active members",
            true,
            FIND_BY_ACTIVE_MEMBERS_CURSOR,
            FIND_BY_ACTIVE_MEMBERS_CURSOR_REVERSED
        ),
        GAMES_PER_ACTIVE_MEMBER
        (
            "Games per active member",
            false,
            FIND_BY_GAMES_PER_ACTIVE_MEMBER_CURSOR,
            FIND_BY_GAMES_PER_ACTIVE_MEMBER_REVERSED
        ),
        AVG_RATING
        (
            "Average rating",
            false,
            FIND_BY_AVG_RATING_CURSOR,
            FIND_BY_AVG_RATING_CURSOR_REVERSED
        );

        private final String name;
        private final boolean defaultt;
        private final String query;
        private final String reversedQuery;

        Cursor(String name, boolean defaultt, String query, String reversedQuery)
        {
            this.name = name;
            this.defaultt = defaultt;
            this.query = query;
            this.reversedQuery = reversedQuery;
        }

        public String getName()
        {
            return name;
        }

        public boolean isDefault()
        {
            return defaultt;
        }

        public String getQuery()
        {
            return query;
        }

        public String getReversedQuery()
        {
            return reversedQuery;
        }

    }

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    @Autowired
    public ClanDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        initMappers(conversionService);
    }

    private static void initMappers(ConversionService conversionService)
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, i)->
            new Clan
            (
                rs.getInt("clan.id"),
                rs.getString("clan.tag"),
                conversionService.convert(rs.getInt("clan.region"), Region.class),
                rs.getString("clan.name"),
                DAOUtils.getInteger(rs, "clan.members"),
                DAOUtils.getInteger(rs, "clan.active_members"),
                DAOUtils.getInteger(rs, "clan.avg_rating"),
                DAOUtils.getConvertedObjectFromInteger(rs, "clan.avg_league_type", conversionService, BaseLeague.LeagueType.class),
                DAOUtils.getInteger(rs, "clan.games")
            );
        if(STD_EXTRACTOR == null) STD_EXTRACTOR = DAOUtils.getResultSetExtractor(STD_ROW_MAPPER);
    }

    public static RowMapper<Clan> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    public static ResultSetExtractor<Clan> getStdExtractor()
    {
        return STD_EXTRACTOR;
    }

    private MapSqlParameterSource createParameterSource(Clan clan)
    {
        return new MapSqlParameterSource()
            .addValue("tag", clan.getTag())
            .addValue("name", clan.getName());
    }

    public Clan[] merge(Clan... clans)
    {
        if(clans.length == 0) return new Clan[0];

        List<Object[]> clanData = Arrays.stream(clans)
            .distinct()
            .map(clan->new Object[]{
                clan.getTag(),
                conversionService.convert(clan.getRegion(), Integer.class),
                clan.getName()
            })
            .collect(Collectors.toList());
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("clans", clanData);

        List<Clan> mergedClans = template.query(MERGE_QUERY, params, STD_ROW_MAPPER);

        return DAOUtils.updateOriginals(clans, mergedClans, (o, m)->o.setId(m.getId()));
    }

    public List<Integer> findIds(Integer cursor, int count)
    {
        SqlParameterSource params = new MapSqlParameterSource()
            .addValue("cursor", cursor)
            .addValue("limit", count);
        return template.query(FIND_IDS_BY_ID_CURSOR, params, DAOUtils.INT_MAPPER);
    }

    public List<Integer> findIdsByMinMemberCount(int minMemberCount)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("minMemberCount", minMemberCount);
        return template.query(FIND_BY_MIN_MEMBER_COUNT, params, DAOUtils.INT_MAPPER);
    }

    public List<Clan> findByIds(Integer... ids)
    {
        if(ids.length == 0) return List.of();

        MapSqlParameterSource params = new MapSqlParameterSource().addValue("ids", List.of(ids));
        return template.query(FIND_BY_IDS, params, STD_ROW_MAPPER);
    }

    public List<Clan> findByTag(String tag)
    {
        SqlParameterSource params = new MapSqlParameterSource().addValue("tag", tag);
        return template.query(FIND_BY_TAG, params, STD_ROW_MAPPER);
    }

    public List<Clan> findByTagOrName(String search)
    {
        String escapedSearch = PostgreSQLUtils.escapeLikePattern(search);
        String nameLike = search.length() >= NAME_LIKE_MIN_LENGTH ? escapedSearch + "%" : escapedSearch;
        SqlParameterSource params = new MapSqlParameterSource()
            .addValue("tag", search)
            .addValue("nameLike", nameLike);
        return template.query(FIND_BY_TAG_OR_NAME, params, STD_ROW_MAPPER);
    }

    public PagedSearchResult<List<Clan>> findByCursor
    (
        Cursor cursor, int cursorVal, int idCursor,
        int minActiveMembers, int maxActiveMembers,
        int minGamesPerActiveMember, int maxGamesPerActiveMember,
        int minAvgRating, int maxAvgRating,
        int page, int pageDiff
    )
    {
        if
        (
            minActiveMembers > maxActiveMembers
            || minGamesPerActiveMember > maxGamesPerActiveMember
            || minAvgRating > maxAvgRating
        ) throw new IllegalArgumentException("Invalid search range. Min values should be less than max values");
        int pageDiffAbs = Math.abs(pageDiff);
        if(pageDiffAbs > MAX_PAGE_DIFF) throw new IllegalArgumentException("Page diff is too big");
        if(page < 0) throw new IllegalArgumentException("Page is negative");
        Objects.requireNonNull(cursor);

        boolean forward = pageDiff > -1;
        long finalPage = page + pageDiff;
        long offset = (long) (pageDiffAbs - 1) * PAGE_SIZE;

        SqlParameterSource params = new MapSqlParameterSource()
            .addValue("cursor", cursorVal)
            .addValue("idCursor", idCursor)
            .addValue("minActiveMembers", minActiveMembers)
            .addValue("maxActiveMembers", maxActiveMembers)
            .addValue("minGamesPerActiveMember", minGamesPerActiveMember)
            .addValue("maxGamesPerActiveMember", maxGamesPerActiveMember)
            .addValue("minAvgRating", minAvgRating)
            .addValue("maxAvgRating", maxAvgRating)
            .addValue("limit", PAGE_SIZE)
            .addValue("offset", offset);

        List<Clan> clans = template.query(forward ? cursor.getQuery() : cursor.getReversedQuery(), params, STD_ROW_MAPPER);
        if(!forward) Collections.reverse(clans);
        return new PagedSearchResult<>(null, (long) PAGE_SIZE, finalPage, clans);
    }

    @Transactional
    public int updateStats()
    {
        int batchIx = 0;
        int count = 0;
        List<Integer> validClans = findIdsByMinMemberCount(CLAN_STATS_MIN_MEMBERS);
        Integer[] races = Arrays.stream(Race.values())
            .map(r->conversionService.convert(r, Integer.class))
            .toArray(Integer[]::new);
        OffsetDateTime from = OffsetDateTime.now().minusDays(CLAN_STATS_DEPTH_DAYS);
        while(batchIx < validClans.size())
        {
            List<Integer> batch = validClans.subList(batchIx, Math.min(batchIx + CLAN_STATS_BATCH_SIZE, validClans.size()));
            MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("clans", batch)
                .addValue("races", races)
                .addValue("from", from);
            count += template.update(UPDATE_STATS, params);
            batchIx += batch.size();
            LOG.trace("Clan stats progress: {}/{} ", batchIx, validClans.size());
        }
        LOG.info("Updates stats of {} clans", count);
        return count;
    }

}
