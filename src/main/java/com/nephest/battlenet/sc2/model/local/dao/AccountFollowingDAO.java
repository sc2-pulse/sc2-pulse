// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.AccountFollowing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AccountFollowingDAO
{

    public static final String CREATE_QUERY =
        "INSERT INTO account_following "
        + "(account_id, following_account_id) "
        + "VALUES (:accountId, :followingAccountId)";
    public static final String DELETE_QUERY =
        "DELETE FROM account_following "
        + "WHERE account_id = :accountId AND following_account_id = :followingAccountId";
    public static final String FOLLOWING_COUNT_QUERY =
        "SELECT COUNT(*) "
        + "FROM account_following "
        + "WHERE account_id = :accountId";
    public static final String FIND_LIST_BY_ACCOUNT_ID_QUERY =
        "SELECT account_id, following_account_id "
        + "FROM account_following "
        + "WHERE account_id = :accountId "
        + "ORDER BY account_id, following_account_id";
    public static final RowMapper<AccountFollowing> STD_ROW_MAPPER = (rs, num)->
        new AccountFollowing(rs.getLong("account_id"), rs.getLong("following_account_id"));

    private final NamedParameterJdbcTemplate template;

    @Autowired
    public AccountFollowingDAO(@Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template)
    {
        this.template = template;
    }

    public AccountFollowing create(AccountFollowing following)
    {
        SqlParameterSource params = new MapSqlParameterSource()
            .addValue("accountId", following.getAccountId())
            .addValue("followingAccountId", following.getFollowingAccountId());
        template.update(CREATE_QUERY, params);
        return following;
    }

    public void delete(long accountId, long followingAccountId)
    {
        SqlParameterSource params = new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("followingAccountId", followingAccountId);
        template.update(DELETE_QUERY, params);
    }

    public int getFollowingCount(long accountId)
    {
        SqlParameterSource params = new MapSqlParameterSource()
            .addValue("accountId", accountId);
        return template.query(FOLLOWING_COUNT_QUERY, params, DAOUtils.INT_EXTRACTOR);
    }

    public List<AccountFollowing> findAccountFollowingList(long accountId)
    {
        SqlParameterSource params = new MapSqlParameterSource()
            .addValue("accountId", accountId);
        return template.query(FIND_LIST_BY_ACCOUNT_ID_QUERY, params, STD_ROW_MAPPER);
    }

}
