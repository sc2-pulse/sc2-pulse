// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.community;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.util.TestUtil;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class VideoStreamImplTest
{

    @Test
    public void testUniqueness()
    {
        TestUtil.testUniqueness
        (
            new VideoStreamImpl
            (
                SocialMedia.TWITCH,
                "id1",
                "uId1",
                "name1",
                "title1",
                Locale.ENGLISH,
                "url1",
                "thumnailUrl1",
                1
            ),
            new VideoStreamImpl
            (
                SocialMedia.TWITCH,
                "id1",
                "uId2",
                "name2",
                "title2",
                Locale.FRENCH,
                "url2",
                "thumnailUrl2",
                2
            ),

            new VideoStreamImpl
            (
                SocialMedia.YOUTUBE,
                "id1",
                "uId1",
                "name1",
                "title1",
                Locale.ENGLISH,
                "url1",
                "thumnailUrl1",
                1
            ),
            new VideoStreamImpl
            (
                SocialMedia.TWITCH,
                "id2",
                "uId1",
                "name1",
                "title1",
                Locale.ENGLISH,
                "url1",
                "thumnailUrl1",
                1
            )
        );
    }

}
