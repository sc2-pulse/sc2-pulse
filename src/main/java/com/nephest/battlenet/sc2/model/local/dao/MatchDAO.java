// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.local.Match;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class MatchDAO
extends StandardDAO
{

    public static final String STD_SELECT =
        "match.id AS \"match.id\", "
        + "match.date AS \"match.date\", "
        + "match.type AS \"match.type\", "
        + "match.map AS \"match.map\", "
        + "match.updated AS \"match.updated\" ";
    private static final String MERGE_QUERY =
        "WITH "
        + "vals AS (VALUES :matchUids), "
        + "updated AS "
        + "("
            + "UPDATE match "
            + "SET updated = NOW() "
            + "FROM vals v (date, type, map)"
            + "WHERE match.date = v.date "
            + "AND match.type = v.type "
            + "AND match.map = v.map "
            + "RETURNING match.id AS \"match.id\", "
            + "match.date AS \"match.date\", "
            + "match.type AS \"match.type\", "
            + "match.map AS \"match.map\", "
            + "match.updated AS \"match.updated\""
        + "), "
        + "missing AS "
        + "("
            + "SELECT v.date, v.type, v.map "
            + "FROM vals v (date, type, map) "
            + "LEFT JOIN updated ON v.date = updated.\"match.date\"  "
            + "AND v.type = updated.\"match.type\" "
            + "AND v.map = updated.\"match.map\" "
            + "WHERE updated.\"match.id\" IS NULL "
        + "), "
        + "inserted AS "
        + "("
            + "INSERT INTO match (date, type, map) "
            + "SELECT * FROM missing "
            /*
                2 matched matches(2 parts from different players) can be absent in the db
                do nothing to verify that this is the case, because it will fail otherwise as there will be no second
                 match in filtering phase
             */
            + "ON CONFLICT(date, type, map) DO NOTHING "
            + "RETURNING id AS \"match.id\", "
            + "date AS \"match.date\", "
            + "type AS \"match.type\", "
            + "map AS \"match.map\", "
            + "updated AS \"match.updated\""
        + ") "
        + "SELECT * FROM updated "
        + "UNION ALL "
        + "SELECT * FROM inserted";

    private static RowMapper<Match> STD_ROW_MAPPER;
    private final ConversionService conversionService;

    @Autowired
    public MatchDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        super(template, "match", "30 DAYS");
        this.conversionService = conversionService;
        initMappers(conversionService);
    }

    private static void initMappers(ConversionService conversionService)
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, i)->
        {
            Match match = new Match
            (
                rs.getLong("match.id"),
                rs.getObject("match.date", OffsetDateTime.class),
                conversionService.convert(rs.getInt("match.type"), BaseMatch.MatchType.class),
                rs.getString("match.map")
            );
            match.setUpdated(rs.getObject("match.updated", OffsetDateTime.class));
            return match;
        };
    }

    public static RowMapper<Match> getStdRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    private MapSqlParameterSource createParameterSource(Match match)
    {
        return new MapSqlParameterSource()
            .addValue("date", match.getDate())
            .addValue("type", conversionService.convert(match.getType(), Integer.class))
            .addValue("map", match.getMap())
            .addValue("updated", match.getUpdated());
    }

    public Match[] merge(Match... matches)
    {
        if(matches.length == 0) return new Match[0];

        List<Object[]> matchUids = Arrays.stream(matches)
            .map(match->new Object[]{
                match.getDate(),
                conversionService.convert(match.getType(), Integer.class),
                match.getMap()
            })
            .collect(Collectors.toList());
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("matchUids", matchUids);

        List<Match> mergedMatches = getTemplate().query(MERGE_QUERY, params, getStdRowMapper());
        Arrays.sort(matches, Comparator.comparing(Match::getDate)
            .thenComparing(Match::getType)
            .thenComparing(Match::getMap));
        mergedMatches.sort(Comparator.comparing(Match::getDate)
            .thenComparing(Match::getType)
            .thenComparing(Match::getMap));

        //expecting ~1% of duplicates, using dirty but efficient way
        for(int originalIx = 0, mergedIx = 0; originalIx < matches.length; originalIx++)
        {
            if(mergedIx >= mergedMatches.size()) mergedIx = mergedMatches.size() - 1; //should rarely happen
            Match original = matches[originalIx];
            Match merged = mergedMatches.get(mergedIx);

            if(!merged.equals(original)) //should rarely happen
            {
                mergedIx--;
                merged = mergedMatches.get(mergedIx);
                if(!merged.equals(original)) throw new IllegalStateException("Match pair didn't match");
            }
            original.setId(merged.getId());
            mergedIx++;
        }

        return matches;
    }

}
