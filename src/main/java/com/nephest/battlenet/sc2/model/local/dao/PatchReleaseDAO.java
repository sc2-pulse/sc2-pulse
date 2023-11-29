// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.PatchRelease;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
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

@Repository
public class PatchReleaseDAO
{

    public static final String STD_SELECT =
        "patch_release.patch_id AS \"patch_release.patch_id\", "
        + "patch_release.region AS \"patch_release.region\", "
        + "patch_release.released AS \"patch_release.released\" ";

    private static final String MERGE =
        "WITH updated AS "
        + "("
            + "UPDATE patch_release "
            + "SET released = :released "
            + "WHERE patch_id = :patchId "
            + "AND region = :region "
            + "AND released != :released "
        + ") "
        + "INSERT INTO patch_release(patch_id, region, released) "
        + "SELECT :patchId, :region, :released "
        + "WHERE NOT EXISTS"
        + "("
            + "SELECT 1 "
            + "FROM patch_release "
            + "WHERE patch_id = :patchId "
            + "AND region = :region "
        + ") ";

    private static final String FIND_BY_PATCH_IDS =
        "SELECT " + STD_SELECT
        + "FROM patch_release "
        + "WHERE patch_id IN(:patchIds)";

    private static RowMapper<PatchRelease> STD_ROW_MAPPER;
    private static ResultSetExtractor<PatchRelease> STD_EXTRACTOR;

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    @Autowired
    public PatchReleaseDAO
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
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, i)->new PatchRelease
        (
            rs.getInt("patch_release.patch_id"),
            conversionService.convert(rs.getInt("patch_release.region"), Region.class),
            rs.getObject("patch_release.released", OffsetDateTime.class)
        );
        if(STD_EXTRACTOR == null) STD_EXTRACTOR = DAOUtils.getResultSetExtractor(STD_ROW_MAPPER);
    }

    public static RowMapper<PatchRelease> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    public static ResultSetExtractor<PatchRelease> getStdExtractor()
    {
        return STD_EXTRACTOR;
    }

    @Transactional
    public int[] merge(Set<PatchRelease> releases)
    {
        if(releases.isEmpty()) return DAOUtils.EMPTY_INT_ARRAY;

        SqlParameterSource[] params = releases.stream()
            .map
            (
                release->new MapSqlParameterSource()
                    .addValue("patchId", release.getPatchId())
                    .addValue
                    (
                        "region",
                        conversionService.convert(release.getRegion(), Integer.class)
                    )
                    .addValue("released", release.getReleased())
            )
            .toArray(SqlParameterSource[]::new);
        return template.batchUpdate(MERGE, params);
    }

    public List<PatchRelease> findByPatchIds(Set<Integer> ids)
    {
        if(ids.isEmpty()) return List.of();

        SqlParameterSource params = new MapSqlParameterSource("patchIds", ids);
        return template.query(FIND_BY_PATCH_IDS, params, getStdRowMapper());
    }

}
