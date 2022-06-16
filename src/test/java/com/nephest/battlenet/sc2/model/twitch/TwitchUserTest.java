// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.twitch;

import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class TwitchUserTest
{

    @Test
    public void testUniqueness()
    {
        TwitchUser user = new TwitchUser(1L, "login1");
        TwitchUser equalUser = new TwitchUser(1L, "login2");
        TwitchUser[] notEqualUsers = new TwitchUser[]
        {
            new TwitchUser(2L, "login1")
        };

        TestUtil.testUniqueness(user, equalUser, notEqualUsers);
    }

}
