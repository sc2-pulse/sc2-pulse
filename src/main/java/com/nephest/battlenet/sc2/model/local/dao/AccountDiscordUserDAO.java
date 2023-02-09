// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.AccountDiscordUser;
import com.nephest.battlenet.sc2.model.local.DiscordUserMeta;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountDiscordUserDAO
{

    private static final String CREATE =
        "INSERT INTO account_discord_user(account_id, discord_user_id) "
        + "VALUES(:accountId, :discordUserId)";

    private static final String FIND_META_BY_ID =
        "SELECT public "
        + "FROM account_discord_user "
        + "WHERE discord_user_id = :discordUserId";

    private static final String FIND_ACCOUNT_IDS = "SELECT account_id FROM account_discord_user";

    private static final String EXISTS_BY_ACCOUNT_ID =
        "SELECT 1 FROM account_discord_user WHERE account_id = :accountId";

    private static final String DELETE_BY_ACCOUNT_ID_OR_DISCORD_USER_ID =
        "DELETE FROM account_discord_user "
        + "WHERE account_id = :accountId "
        + "OR discord_user_id = :discordUserId";

    private static final String UPDATE_PUBLIC_FLAG =
        "UPDATE account_discord_user "
        + "SET public = :public "
        + "WHERE account_id = :accountId";

    private final NamedParameterJdbcTemplate template;

    public static final RowMapper<DiscordUserMeta> META_MAPPER = (rs, rowNum)->new AccountDiscordUser
    (
        null,
        null,
        rs.getBoolean("public")
    );

    public static final ResultSetExtractor<DiscordUserMeta> META_EXTRACTOR =
        DAOUtils.getResultSetExtractor(META_MAPPER);

    @Autowired
    public AccountDiscordUserDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template
    )
    {
        this.template = template;
    }

    public int[] create(AccountDiscordUser... users)
    {
        if(users.length == 0) return DAOUtils.EMPTY_INT_ARRAY;

        MapSqlParameterSource[] params = Arrays.stream(users)
            .filter(Objects::nonNull)
            .distinct()
            .map
            (
                u->new MapSqlParameterSource()
                    .addValue("accountId", u.getAccountId())
                    .addValue("discordUserId", u.getDiscordUserId())
            )
            .toArray(MapSqlParameterSource[]::new);
        return template.batchUpdate(CREATE, params);
    }

    public Optional<DiscordUserMeta> findMeta(Long discordUserId)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("discordUserId", discordUserId);
        return Optional.ofNullable(template.query(FIND_META_BY_ID, params, META_EXTRACTOR));
    }

    public Set<Long> findAccountIds()
    {
        return template.queryForStream(FIND_ACCOUNT_IDS, Map.of(), DAOUtils.LONG_MAPPER)
            .collect(Collectors.toSet());
    }

    public boolean existsByAccountId(long accountId)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("accountId", accountId);
        Integer exists = template.query(EXISTS_BY_ACCOUNT_ID, params, DAOUtils.INT_EXTRACTOR);
        return exists != null;
    }

    public int remove(Long accountId, Long discordUserId)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("discordUserId", discordUserId);

        return template.update(DELETE_BY_ACCOUNT_ID_OR_DISCORD_USER_ID, params);
    }

    public boolean updatePublicFlag(long accountId, boolean isPublic)
    {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("public", isPublic ? true : null);
        return template.update(UPDATE_PUBLIC_FLAG, params) == 1;
    }

}
