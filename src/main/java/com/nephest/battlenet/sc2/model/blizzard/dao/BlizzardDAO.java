// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard.dao;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardPlayerCharacter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class BlizzardDAO
{

    private static final String FIND_ALTERNATIVE_LADDER_IDS =
        "WITH team_filter AS "
        + "("
            + "SELECT team.id, team.division_id, ROW_NUMBER() OVER(PARTITION BY team.division_id) AS rownum "
            + "FROM team "
            + "WHERE team.season = :season "
            + "AND team.region IN(:regions) "
            + "AND team.queue_type IN(:queues) "
            + "AND team.league_type IN(:leagues) "
        + "), "
        + "team_filter_limit AS "
        + "("
            + "SELECT team_filter.id, division.battlenet_id AS \"division.battlenet_id\" "
            + "FROM team_filter "
            + "INNER JOIN division ON team_filter.division_id = division.id "
            + "WHERE team_filter.rownum <= :playerCharacterCount "
        + ") "
        + "SELECT DISTINCT ON(team_filter_limit.\"division.battlenet_id\", player_character.region, team_filter_limit.id) "
        + "player_character.realm, "
        + "player_character.battlenet_id, "
        + "player_character.name, "
        + "player_character.region, "
        + "team_filter_limit.\"division.battlenet_id\" "
        + "FROM team_filter_limit "
        + "INNER JOIN team_member ON team_member.team_id = team_filter_limit.id "
        + "INNER JOIN player_character ON team_member.player_character_id = player_character.id "
        + "ORDER BY team_filter_limit.\"division.battlenet_id\", player_character.region, team_filter_limit.id";

    private static ResultSetExtractor<List<Tuple3<Region, BlizzardPlayerCharacter[], Long>>> LEGACY_ID_EXTRACTOR;

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    @Autowired
    public BlizzardDAO
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
        if(LEGACY_ID_EXTRACTOR == null) LEGACY_ID_EXTRACTOR = rs->
        {
            List<Tuple3<Region, BlizzardPlayerCharacter[], Long>> ids = new ArrayList<>();
            long lastDivision = -1;
            Region lastRegion = null;
            List<BlizzardPlayerCharacter> currentCharacters = new ArrayList<>();
            while(rs.next())
            {
                long division = rs.getLong("division.battlenet_id");
                Region region = conversionService.convert(rs.getInt("region"), Region.class);
                if(division != lastDivision || region != lastRegion)
                {
                    if(currentCharacters.size() > 0)
                        ids.add(Tuples.of(lastRegion, currentCharacters.toArray(BlizzardPlayerCharacter[]::new), lastDivision));
                    lastDivision = division;
                    lastRegion = region;
                    currentCharacters.clear();
                }
                currentCharacters.add(new BlizzardPlayerCharacter(
                    rs.getLong("battlenet_id"),
                    rs.getInt("realm"),
                    rs.getString("name")
                ));
            }
            ids.add(Tuples.of(lastRegion, currentCharacters.toArray(BlizzardPlayerCharacter[]::new), lastDivision));
            return ids;
        };
    }

    public List<Tuple3<Region, BlizzardPlayerCharacter[], Long>> findLegacyLadderIds
    (int season, Region[] regions, QueueType[] queues, BaseLeague.LeagueType[] leagues, int characterCount)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("season", season)
            .addValue("regions", Arrays.stream(regions)
                .map(r->conversionService.convert(r, Integer.class)).collect(Collectors.toList()))
            .addValue("queues", Arrays.stream(queues)
                .map(q->conversionService.convert(q, Integer.class)).collect(Collectors.toList()))
            .addValue("leagues", Arrays.stream(leagues)
                .map(l->conversionService.convert(l, Integer.class)).collect(Collectors.toList()))
            .addValue("playerCharacterCount", characterCount);
        return template.query(FIND_ALTERNATIVE_LADDER_IDS, params, LEGACY_ID_EXTRACTOR);
    }

}
