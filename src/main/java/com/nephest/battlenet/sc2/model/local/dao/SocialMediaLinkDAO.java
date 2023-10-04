// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.SocialMediaLink;
import com.nephest.battlenet.sc2.model.local.SocialMediaUserId;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class SocialMediaLinkDAO
{

    public static final String STD_SELECT =
        "social_media_link.pro_player_id \"social_media_link.pro_player_id\", "
        + "social_media_link.type AS \"social_media_link.type\", "
        + "social_media_link.url AS \"social_media_link.url\", "
        + "social_media_link.updated AS \"social_media_link.updated\", "
        + "social_media_link.service_user_id AS \"social_media_link.service_user_id\", "
        + "social_media_link.protected AS \"social_media_link.protected\" ";

    public static final String MERGE_QUERY =
        "WITH updated AS "
        + "("
            + "UPDATE social_media_link "
            + "SET url = :url, "
            + "updated = :updated, "
            + "service_user_id = :serviceUserId, "
            + "protected = :protected "
            + "WHERE pro_player_id = :proPlayerId "
            + "AND type = :type "
            + "AND(:protectedMode = false OR protected IS NULL) "
            + "AND "
            + "( "
                + "url != :url "
                + "OR protected IS DISTINCT FROM :protected "
            + ") "
            + "RETURNING pro_player_id "
        + "), "
        + "inserted AS "
        + "("
            + "INSERT INTO social_media_link "
            + "("
                + "pro_player_id, "
                + "type, "
                + "url, "
                + "updated, "
                + "service_user_id, "
                + "protected"
            + ") "
            + "SELECT :proPlayerId, :type, :url, :updated, :serviceUserId, :protected "
            + "WHERE NOT EXISTS"
            + "("
                + "SELECT 1 "
                + "FROM social_media_link "
                + "WHERE pro_player_id = :proPlayerId "
                + "AND type = :type "
            + ") "
            + "ON CONFLICT(pro_player_id, type) DO UPDATE SET "
            + "url=excluded.url,"
            + "updated=excluded.updated, "
            + "service_user_id=excluded.service_user_id, "
            + "protected = excluded.protected "
            + "RETURNING pro_player_id "
        + ") "
        + "SELECT pro_player_id "
        + "FROM updated "
        + "UNION "
        + "SELECT pro_player_id "
        + "FROM inserted ";
    private static final String DELETE =
        "DELETE FROM social_media_link "
        + "WHERE pro_player_id = :proPlayerId "
        + "AND type = :type";
    private static final String FIND_LIST_BY_TYPE =
        "SELECT " + STD_SELECT + ", " + ProPlayerDAO.STD_SELECT + " "
        + "FROM social_media_link "
        + "INNER JOIN pro_player ON pro_player.id = social_media_link.pro_player_id "
        + "WHERE type = :type";
    private static final String FIND_LIST_BY_TYPES =
        "SELECT " + STD_SELECT + "FROM social_media_link WHERE type IN(:types)";

    private static final String FIND_BY_PRO_PLAYER_IDS =
        "SELECT " + STD_SELECT
        + "FROM social_media_link "
        + "WHERE pro_player_id IN(:proPlayerIds) "
        + "ORDER BY pro_player_id, type";

    private static final String FIND_BY_TYPE_AND_SERVICE_USER_IDS =
        "SELECT " + STD_SELECT
        + "FROM social_media_link "
        + "WHERE (type, service_user_id) IN(:serviceUserIds)";

    private static final String FIND_BY_ID_CURSOR_AND_TYPE =
        "SELECT " + STD_SELECT
        + "FROM social_media_link "
        + "WHERE pro_player_id > :proPlayerIdCursor "
        + "AND type = :type "
        + "ORDER BY pro_player_id "
        + "LIMIT :limit";

    private static RowMapper<SocialMediaLink> STD_ROW_MAPPER;
    private static ResultSetExtractor<Map<ProPlayer, SocialMediaLink>> GROUP_FETCH_MAPPER;

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    @Autowired
    public SocialMediaLinkDAO
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
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, ix)-> new SocialMediaLink
        (
            rs.getLong("social_media_link.pro_player_id"),
            conversionService.convert(rs.getInt("social_media_link.type"), SocialMedia.class),
            rs.getString("social_media_link.url"),
            rs.getObject("social_media_link.updated", OffsetDateTime.class),
            rs.getString("social_media_link.service_user_id"),
            rs.getBoolean("social_media_link.protected")
        );

        if(GROUP_FETCH_MAPPER == null) GROUP_FETCH_MAPPER = (rs)->
        {
            Map<ProPlayer, SocialMediaLink> result = new HashMap<>();
            while(rs.next())
                result.put(ProPlayerDAO.getStdRowMapper().mapRow(rs, 0), getStdRowMapper().mapRow(rs, 0));

            return result;
        };
    }

    public static RowMapper<SocialMediaLink> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    public static ResultSetExtractor<Map<ProPlayer, SocialMediaLink>> getGroupFetchMapper()
    {
        return GROUP_FETCH_MAPPER;
    }

    private MapSqlParameterSource createParameterSource(SocialMediaLink link)
    {
        return new MapSqlParameterSource()
            .addValue("proPlayerId", link.getProPlayerId())
            .addValue("type", conversionService.convert(link.getType(), Integer.class))
            .addValue("url", link.getUrl())
            .addValue("updated", link.getUpdated())
            .addValue("serviceUserId", link.getServiceUserId())
            .addValue("protected", link.isProtected() ? link.isProtected() : null);
    }

    public Set<SocialMediaLink> merge(Set<SocialMediaLink> links)
    {
        return merge(false, links);
    }

    /**
     * <p>
     *     This method merges the links with an optional protection. If protection flag is set to
     *     true, then links will be updated only when their isProtected flag is set to false.
     *     This method exists to protect selected links from invalid updates from upstream
     *     sources.
     * </p>
     *
     * @param isProtected protection flag
     * @param links links to merge
     * @return merged links
     */
    public Set<SocialMediaLink> merge(boolean isProtected, Set<SocialMediaLink> links)
    {
        if(links.isEmpty()) return links;

        MapSqlParameterSource[] params = links.stream()
            .filter(Objects::nonNull)
            .sorted(SocialMediaLink.NATURAL_ID_COMPARATOR)
            .peek(link->link.setUpdated(link.getUpdated() != null
                ? link.getUpdated()
                : OffsetDateTime.now()))
            .map(link->createParameterSource(link).addValue("protectedMode", isProtected))
            .toArray(MapSqlParameterSource[]::new);

        template.batchUpdate(MERGE_QUERY, params);
        return links;
    }

    public void remove(Set<SocialMediaLink> links)
    {
        if(links.isEmpty()) return;

        MapSqlParameterSource[] params = links.stream()
            .filter(Objects::nonNull)
            .sorted(SocialMediaLink.NATURAL_ID_COMPARATOR)
            .map
            (
                link->new MapSqlParameterSource()
                    .addValue("proPlayerId", link.getProPlayerId())
                    .addValue("type", conversionService.convert(link.getType(), Integer.class))
            )
            .toArray(MapSqlParameterSource[]::new);
        template.batchUpdate(DELETE, params);
    }

    public Map<ProPlayer, SocialMediaLink> findGroupedListByType(SocialMedia type)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("type", conversionService.convert(type, Integer.class));
        return template.query(FIND_LIST_BY_TYPE, params, getGroupFetchMapper());
    }

    public List<SocialMediaLink> findByTypes(Set<SocialMedia> types)
    {
        if(types.isEmpty()) return List.of();

        List<Integer> typeInts = types.stream()
            .map(t->conversionService.convert(t, Integer.class))
            .collect(Collectors.toList());
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("types", typeInts);
        return template.query(FIND_LIST_BY_TYPES, params, STD_ROW_MAPPER);
    }

    public List<SocialMediaLink> find(Set<Long> proPlayerIds)
    {
        if(proPlayerIds.isEmpty()) return List.of();

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("proPlayerIds", proPlayerIds);
        return template.query(FIND_BY_PRO_PLAYER_IDS, params, STD_ROW_MAPPER);
    }

    public List<SocialMediaLink> findByServiceUserIds(Set<SocialMediaUserId> serviceUserIds)
    {
        if(serviceUserIds.isEmpty()) return List.of();

        List<Object[]> data = serviceUserIds.stream()
            .map(id->new Object[]{
                conversionService.convert(id.getType(), Integer.class),
                id.getServiceUserid()
            })
            .collect(Collectors.toList());
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("serviceUserIds", data);
        return template.query(FIND_BY_TYPE_AND_SERVICE_USER_IDS, params, STD_ROW_MAPPER);
    }

    public List<SocialMediaLink> findByIdCursor
    (
        Long proPlayerIdCursor,
        SocialMedia type,
        int limit
    )
    {
        SqlParameterSource params = new MapSqlParameterSource()
            .addValue("proPlayerIdCursor", proPlayerIdCursor != null ? proPlayerIdCursor : -1L)
            .addValue("type", conversionService.convert(type, Integer.class))
            .addValue("limit", limit);
        return template.query(FIND_BY_ID_CURSOR_AND_TYPE, params, STD_ROW_MAPPER);
    }

}
