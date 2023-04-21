// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.ladder.RevealerStats;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LadderProPlayerAccountDAO
{

    private static final String FIND_REVEALER_STATS =
        "WITH stats AS "
        + "( "
            + "SELECT revealer_account_id, "
            + "COUNT(*) AS accounts_revealed "
            + "FROM pro_player_account "
            + "WHERE revealer_account_id IS NOT NULL "
            + "GROUP BY revealer_account_id "
            + "ORDER BY accounts_revealed DESC "
            + "LIMIT :limit"
        + ") "
        + "SELECT " + AccountDAO.STD_SELECT + ", "
        + "stats.accounts_revealed "
        + "FROM stats "
        + "INNER JOIN account ON stats.revealer_account_id = account.id "
        + "ORDER BY accounts_revealed DESC";

    private final NamedParameterJdbcTemplate template;

    private static RowMapper<RevealerStats> REVEALER_STATS_ROW_MAPPER;

    public LadderProPlayerAccountDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template
    )
    {
        this.template = template;
        initMappers();
    }

    private void initMappers()
    {
        if(REVEALER_STATS_ROW_MAPPER == null) REVEALER_STATS_ROW_MAPPER = (rs, i)->new RevealerStats
        (
            AccountDAO.getStdRowMapper().mapRow(rs, i),
            rs.getInt("accounts_revealed")
        );
    }

    public List<RevealerStats> findRevealerStats(int limit)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("limit", limit);
        return template.query(FIND_REVEALER_STATS, params, REVEALER_STATS_ROW_MAPPER);
    }

}
