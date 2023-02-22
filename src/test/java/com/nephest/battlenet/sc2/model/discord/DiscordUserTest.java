// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.discord;

import com.nephest.battlenet.sc2.util.TestUtil;
import discord4j.common.util.Snowflake;
import org.junit.jupiter.api.Test;

public class DiscordUserTest
{

    @Test
    public void testUniqueness()
    {
        DiscordUser user = new DiscordUser(Snowflake.of(1L), "name1", 1);
        DiscordUser equalUser = new DiscordUser(Snowflake.of(1L), "name2", 2);
        DiscordUser notEqualUser = new DiscordUser(Snowflake.of(2L), "name1", 1);

        TestUtil.testUniqueness(user, equalUser, notEqualUser);
    }

}
