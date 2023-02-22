// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.discord.dao;

import discord4j.common.util.Snowflake;
import org.springframework.jdbc.core.RowMapper;

public final class DiscordDAOUtil
{

    public static final RowMapper<Snowflake> SNOWFLAKE_MAPPER = (rs, ix)->
    {
        Long val = rs.getLong(1);
        return rs.wasNull() ? null : Snowflake.of(val);
    };

    private DiscordDAOUtil(){}

}
