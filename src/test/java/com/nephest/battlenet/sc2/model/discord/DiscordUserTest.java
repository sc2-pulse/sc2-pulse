// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.util.TestUtil;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @CsvSource
    ({
        "0,",
        "1, 1"
    })
    @ParameterizedTest
    public void testUpstreamDiscriminator(String in, Integer out)
    {
        User user = mock(User.class);
        when(user.getId()).thenReturn(Snowflake.of(123L));
        when(user.getUsername()).thenReturn("name123");
        when(user.getDiscriminator()).thenReturn(in);

        DiscordUser discordUser = DiscordUser.from(user);
        assertEquals(out, discordUser.getDiscriminator());
    }

}
