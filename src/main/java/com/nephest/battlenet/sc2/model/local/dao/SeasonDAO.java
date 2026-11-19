// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Season;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SeasonDAO
{
    public static final String STD_SELECT =
        "season.id AS \"season.id\","
        + "season.battlenet_id AS \"season.battlenet_id\","
        + "season.pulse_season_id AS \"season.pulse_season_id\","
        + "season.region AS \"season.region\","
        + "season.year AS \"season.year\","
        + "season.number AS \"season.number\","
        + "season.\"start\" AS \"season.start\", "
        + "season.\"end\" AS \"season.end\" ";

    private static final String COLUMNS =
        "battlenet_id, pulse_season_id, region, year, number, "
        + "\"start\", \"end\"";
    private static final String PARAMETERS =
        ":battlenetId, :pulseSeasonId, :region, :year, :number, :start, :end";

    private static final String CREATE_QUERY = "INSERT INTO season "
        + "(" + COLUMNS + ") "
        + "VALUES (" + PARAMETERS + ")";

    /*
        pulse_season_id is an identity of the season, it's set only when
        inserting a new season. Year and number updates of an existing season
        are rejected by fk_season_pulse_season_id_year_number.
     */
    private static final String MERGE_QUERY =
        "WITH "
        + "vals AS (VALUES(" + PARAMETERS + ")), "
        + "selected AS "
        + "("
            + "SELECT id, region, battlenet_id "
            + "FROM season "
            + "INNER JOIN vals v(" + COLUMNS + ") "
                + "USING (region, battlenet_id) "
        + "), "
        + "updated AS "
        + "("
            + "UPDATE season "
            + "SET year = v.year, "
            + "number = v.number, "
            + "\"start\" = v.start, "
            + "\"end\" = v.end "
            + "FROM selected "
            + "INNER JOIN vals v(" + COLUMNS + ") "
                + "USING (region, battlenet_id) "
            + "WHERE season.id = selected.id "
            + "AND"
            + "("
                + "season.year != v.year "
                + "OR season.number != v.number "
                + "OR season.\"start\" != v.start "
                + "OR season.\"end\" != v.end "
            + ") "
        + "), "
        + "inserted AS "
        + "("
            + "INSERT INTO season "
            + "(" + COLUMNS + ") "
            + "SELECT * FROM vals "
            + "WHERE NOT EXISTS (SELECT 1 FROM selected) "
            + "ON CONFLICT(region, battlenet_id) DO UPDATE SET "
            + "year=excluded.year, "
            + "number=excluded.number, "
            + "\"start\"=excluded.start, "
            + "\"end\"=excluded.end "
            + "RETURNING id "
        + ") "
        + "SELECT id FROM selected "
        + "UNION "
        + "SELECT id FROM inserted";

    private static final String FIND_LIST_BY_IDS = "SELECT "
        + STD_SELECT
        + "FROM season "
        + "WHERE id IN(:ids)";

    private static final String FIND_BY_REGION_AND_BATTLENET_ID =
        """
            SELECT %1$s
            FROM season
            WHERE region = :region
            AND battlenet_id = :battlenetId;
        """.formatted(STD_SELECT);

    private static final String FIND_BY_REGION_AND_PULSE_SEASON_ID =
        """
            SELECT %1$s
            FROM season
            WHERE region = :region
            AND pulse_season_id = :pulseSeasonId;
        """.formatted(STD_SELECT);

    private static final String FIND_LIST_BY_REGION = "SELECT "
        + STD_SELECT
        + "FROM season "
        + "WHERE region=:region "
        + "ORDER BY pulse_season_id DESC";

    private static final String FIND_LIST_BY_PULSE_SEASON_ID =
        "SELECT " + STD_SELECT
        + "FROM season "
        + "WHERE :pulseSeasonId IS NULL "
        + "OR season.pulse_season_id = :pulseSeasonId "
        + "ORDER BY pulse_season_id DESC, region DESC";

    private static final String FIND_LIST_BY_FIRST_PULSE_SEASON_ID =
        "SELECT DISTINCT ON (pulse_season_id) "
        + STD_SELECT
        + "FROM season "
        + "ORDER BY pulse_season_id DESC, region DESC";

    private static final String FIND_LAST =
        "SELECT " + STD_SELECT
        + "FROM season "
        + "ORDER BY pulse_season_id DESC, region DESC "
        + "LIMIT 1";

    private static final String FIND_MAX_PULSE_SEASON_ID_QUERY =
        "SELECT MAX(pulse_season_id) FROM season";

    private static final String FIND_MAX_PULSE_SEASON_ID_BY_REGION_QUERY =
        FIND_MAX_PULSE_SEASON_ID_QUERY + " WHERE region = :region";

    private static final String FIND_MAX_BATTLENET_ID_BY_REGION_QUERY =
        "SELECT MAX(battlenet_id) FROM season WHERE region = :region";

    private static final String FIND_LAST_IN_ALL_REGIONS =
        "SELECT DISTINCT ON (region) "
        + STD_SELECT
        + "FROM season "
        + "ORDER BY region DESC, pulse_season_id DESC ";

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;
    private final PulseSeasonDAO pulseSeasonDAO;

    private static RowMapper<Season> STD_ROW_MAPPER;
    private static ResultSetExtractor<Season> STD_EXTRACTOR;

    @Autowired
    public SeasonDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService")
        ConversionService conversionService,
        PulseSeasonDAO pulseSeasonDAO
    )
    {
        this.template = template;
        this.conversionService = conversionService;
        this.pulseSeasonDAO = pulseSeasonDAO;
        initMappers(conversionService);
    }

    private static void initMappers(ConversionService conversionService)
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER =
        (rs, num)-> new Season
        (
            rs.getInt("season.id"),
            rs.getInt("season.battlenet_id"),
            rs.getInt("season.pulse_season_id"),
            conversionService.convert(rs.getInt("season.region"), Region.class),
            rs.getInt("season.year"),
            rs.getInt("season.number"),
            rs.getObject("season.start", OffsetDateTime.class),
            rs.getObject("season.end", OffsetDateTime.class)
        );
        if(STD_EXTRACTOR == null) STD_EXTRACTOR = DAOUtils.getResultSetExtractor(STD_ROW_MAPPER);
    }

    public static RowMapper<Season> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    public static ResultSetExtractor<Season> getStdExtractor()
    {
        return STD_EXTRACTOR;
    }

    /**
     * Creates a new season. Its pulse season id is always resolved from
     * year and number, the original value is ignored.
     *
     * @param season new season
     * @return created season
     */
    @Transactional
    public Season create(Season season)
    {
        season.setPulseSeasonId(mergePulseSeason(season));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = createParameterSource(season);
        template.update(CREATE_QUERY, params, keyHolder, new String[]{"id"});
        season.setId(keyHolder.getKey().intValue());
        return season;
    }

    /**
     * Updates an existing season or creates a new one. Its pulse season id is
     * always resolved from year and number, the original value is ignored.
     * Year and number of an existing season can't be changed because they
     * must match its pulse season, which is enforced by the DB.
     *
     * @param season season to merge
     * @return merged season with id and pulse season id
     * @throws org.springframework.dao.DataIntegrityViolationException if year
     * or number of an existing season is changed
     */
    @Transactional
    public Season merge(Season season)
    {
        season.setPulseSeasonId(mergePulseSeason(season));
        MapSqlParameterSource params = createParameterSource(season);
        season.setId(template.query(MERGE_QUERY, params, DAOUtils.INT_EXTRACTOR));
        return season;
    }

    private int mergePulseSeason(Season season)
    {
        return pulseSeasonDAO.merge
        (
            season.getYear(),
            season.getNumber(),
            season.getBattlenetId()
        )
            .getId();
    }

    private MapSqlParameterSource createParameterSource(Season season)
    {
        return new MapSqlParameterSource()
            .addValue("battlenetId", season.getBattlenetId())
            .addValue("pulseSeasonId", season.getPulseSeasonId())
            .addValue("region", conversionService.convert(season.getRegion(), Integer.class))
            .addValue("year", season.getYear())
            .addValue("number", season.getNumber())
            .addValue("start", season.getStart())
            .addValue("end", season.getEnd());
    }

    public List<Season> findListByIds(List<Integer> ids)
    {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("ids", ids);
        return template.query(FIND_LIST_BY_IDS, params, STD_ROW_MAPPER);
    }

    public Optional<Season> find(Region region, int battlenetId)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("region", conversionService.convert(region, Integer.class))
            .addValue("battlenetId", battlenetId);
        return Optional.ofNullable(template.query(
            FIND_BY_REGION_AND_BATTLENET_ID, params, STD_EXTRACTOR));
    }

    public Optional<Season> findByPulseSeasonId
    (
        Region region,
        int pulseSeasonId
    )
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue
            (
                "region",
                conversionService.convert(region, Integer.class)
            )
            .addValue("pulseSeasonId", pulseSeasonId);
        return Optional.ofNullable(template.query(
            FIND_BY_REGION_AND_PULSE_SEASON_ID, params, STD_EXTRACTOR));
    }

    public List<Season> findListByRegion(Region region)
    {
        MapSqlParameterSource params
            = new MapSqlParameterSource("region", conversionService.convert(region, Integer.class));
        return template.query(FIND_LIST_BY_REGION, params, STD_ROW_MAPPER);
    }

    public List<Season> findListByFirstPulseSeasonId()
    {
        return template
            .query(FIND_LIST_BY_FIRST_PULSE_SEASON_ID, STD_ROW_MAPPER);
    }

    public Optional<Season> findLast()
    {
        return Optional.ofNullable(template.query(FIND_LAST, STD_EXTRACTOR));
    }

    public List<Season> findLastInAllRegions()
    {
        return template.query(FIND_LAST_IN_ALL_REGIONS, STD_ROW_MAPPER);
    }

    /**
     * @return distinct pulse season ids of last seasons in all regions
     */
    public List<Integer> getLastInAllRegions()
    {
        return findLastInAllRegions().stream()
            .map(Season::getPulseSeasonId)
            .distinct()
            .collect(Collectors.toList());
    }

    @Cacheable
    (
        cacheNames = "fqdn-ladder-scan",
        keyGenerator = "fqdnSimpleKeyGenerator"
    )
    public List<Season> findListByPulseSeasonId(@Nullable Integer pulseSeasonId)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("pulseSeasonId", pulseSeasonId, Types.INTEGER);
        return template
            .query(FIND_LIST_BY_PULSE_SEASON_ID, params, STD_ROW_MAPPER);
    }

    @Cacheable
    (
        cacheNames = "fqdn-ladder-scan",
        keyGenerator = "fqdnSimpleKeyGenerator"
    )
    public Integer getMaxPulseSeasonId()
    {
        return template
            .query(FIND_MAX_PULSE_SEASON_ID_QUERY, DAOUtils.INT_EXTRACTOR);
    }

    @Cacheable
    (
        cacheNames = "fqdn-ladder-scan",
        keyGenerator = "fqdnSimpleKeyGenerator"
    )
    public Integer getMaxPulseSeasonId(Region region)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue
            (
                "region",
                conversionService.convert(region, Integer.class)
            );
        return template.query
        (
            FIND_MAX_PULSE_SEASON_ID_BY_REGION_QUERY,
            params,
            DAOUtils.INT_EXTRACTOR
        );
    }

    @Cacheable(cacheNames = "fqdn-ladder-scan", keyGenerator = "fqdnSimpleKeyGenerator")
    public Integer getMaxBattlenetId(Region region)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("region", conversionService.convert(region, Integer.class));
        return template.query(FIND_MAX_BATTLENET_ID_BY_REGION_QUERY, params, DAOUtils.INT_EXTRACTOR);
    }

    public RowMapper<Season> getStandardRowMapper()
    {
        return STD_ROW_MAPPER;
    }

}
