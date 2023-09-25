// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class SocialMediaUserIdTest
{

    @Test
    public void testUniqueness()
    {
        TestUtil.testUniqueness
        (
            new SocialMediaUserId(SocialMedia.TWITCH, "1"),
            new SocialMediaUserId(SocialMedia.TWITCH, "1"),

            new SocialMediaUserId(SocialMedia.TWITCH, "2"),
            new SocialMediaUserId(SocialMedia.YOUTUBE, "1")
        );
    }

}
