// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.SocialMediaLink;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SocialMediaLinkDAO
{

    public static final String STD_SELECT =
        "social_media_link.pro_player_id \"social_media_link.pro_player_id\", "
        + "social_media_link.type AS \"social_media_link.type\", "
        + "social_media_link.url AS \"social_media_link.url\", "
        + "social_media_link.updated AS \"social_media_link.updated\", "
        + "social_media_link.protected AS \"social_media_link.protected\" ";
    private static final String CREATE_QUERY =
        "INSERT INTO social_media_link (pro_player_id, type, url, updated, protected) "
            + "VALUES(:proPlayerId, :type, :url, :updated, :protected) ";
    private static final String MERGE_QUERY = CREATE_QUERY + " "
        + "ON CONFLICT(pro_player_id, type) DO UPDATE SET "
        + "url=excluded.url,"
        + "updated=excluded.updated, "
        + "protected = excluded.protected ";
    private static final String PROTECTED_MERGE_QUERY = MERGE_QUERY + " "
        + "WHERE social_media_link.protected IS NULL";
    private static final String FIND_LIST_BY_TYPE =
        "SELECT " + STD_SELECT + ", " + ProPlayerDAO.STD_SELECT + " "
        + "FROM social_media_link "
        + "INNER JOIN pro_player ON pro_player.id = social_media_link.pro_player_id "
        + "WHERE type = :type";
    private static final String FIND_LIST_BY_TYPES =
        "SELECT " + STD_SELECT + "FROM social_media_link WHERE type IN(:types)";

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
            .addValue("protected", link.isProtected() ? link.isProtected() : null);
    }

    public int[] merge(SocialMediaLink... links)
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
     * @return number of saved links
     */
    public int[] merge(boolean isProtected, SocialMediaLink... links)
    {
        if(links.length == 0) return DAOUtils.EMPTY_INT_ARRAY;

        MapSqlParameterSource[] params = new MapSqlParameterSource[links.length];
        for(int i = 0; i < links.length; i++)
        {
            links[i].setUpdated(OffsetDateTime.now());
            params[i] = createParameterSource(links[i]);
        }

        return template.batchUpdate(isProtected ? PROTECTED_MERGE_QUERY : MERGE_QUERY, params);
    }

    public Map<ProPlayer, SocialMediaLink> findGroupedListByType(SocialMedia type)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("type", conversionService.convert(type, Integer.class));
        return template.query(FIND_LIST_BY_TYPE, params, getGroupFetchMapper());
    }

    public List<SocialMediaLink> findByTypes(SocialMedia... types)
    {
        if(types.length == 0) return List.of();

        List<Integer> typeInts = Arrays.stream(types)
            .map(t->conversionService.convert(t, Integer.class))
            .collect(Collectors.toList());
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("types", typeInts);
        return template.query(FIND_LIST_BY_TYPES, params, STD_ROW_MAPPER);
    }

}
