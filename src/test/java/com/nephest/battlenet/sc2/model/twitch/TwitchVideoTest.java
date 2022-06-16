// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.twitch;

import com.nephest.battlenet.sc2.util.TestUtil;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

public class TwitchVideoTest
{

    @Test
    public void testUniqueness()
    {
        OffsetDateTime equalDate = OffsetDateTime.now();
        OffsetDateTime notEqualDate = equalDate.minusDays(1);
        TwitchVideo video = new TwitchVideo(1L, 1L, "url1", equalDate, equalDate);
        TwitchVideo equalVideo = new TwitchVideo(1L, 2L, "url2", notEqualDate, notEqualDate);
        TwitchVideo[] notEqualVideos = new TwitchVideo[]
        {
            new TwitchVideo(2L, 1L, "url1", equalDate, equalDate)
        };
        TestUtil.testUniqueness(video, equalVideo, notEqualVideos);
    }

}
