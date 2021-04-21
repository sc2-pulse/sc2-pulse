// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.model.local.Period;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonStateDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderSeasonState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class LadderSeasonStateDAO
{

    private static final String FIND_TEMPLATE =
        "SELECT " + SeasonDAO.STD_SELECT + ", " + SeasonStateDAO.STD_SELECT + " "
        + "FROM season_state "
        + "INNER JOIN season ON season_state.season_id = season.id "
        + "WHERE season_state.\"timestamp\" >= :to - INTERVAL '%1$s'"
        + "AND season_state.\"timestamp\" < :to "
        + "ORDER BY season_state.\"timestamp\", season.region";
    private static final Map<Period, String> FIND_QUERIES = Arrays.stream(Period.values())
        .collect(Collectors.toUnmodifiableMap(Function.identity(), p->String.format(FIND_TEMPLATE, p.getSqlPeriod())));

    private static RowMapper<LadderSeasonState> STD_ROW_MAPPER;

    private final NamedParameterJdbcTemplate template;

    @Autowired
    public LadderSeasonStateDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        SeasonDAO seasonDAO
    )
    {
        this.template = template;
        initMappers(seasonDAO);
    }

    private static void initMappers(SeasonDAO seasonDAO)
    {
        if(STD_ROW_MAPPER == null) STD_ROW_MAPPER = (rs, i)-> new LadderSeasonState(
            SeasonStateDAO.STD_ROW_MAPPER.mapRow(rs, 1),
            seasonDAO.getStandardRowMapper().mapRow(rs, 1)
        );
    }

    public static RowMapper<LadderSeasonState> getStandardRowMapper()
    {
        return STD_ROW_MAPPER;
    }

    public List<LadderSeasonState> find(OffsetDateTime to, Period period)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("to", to);
        return template.query(FIND_QUERIES.get(period), params, getStandardRowMapper());
    }

}
