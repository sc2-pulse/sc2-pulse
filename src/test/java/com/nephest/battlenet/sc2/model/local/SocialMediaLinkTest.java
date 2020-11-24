// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;

public class SocialMediaLinkTest
{

    @Test
    public void testUniqueness()
    {
        SocialMediaLink link = new SocialMediaLink(1L, SocialMedia.ALIGULAC, "url");
        SocialMediaLink equalLink = new SocialMediaLink(1L, SocialMedia.ALIGULAC, "doesn't matter");
        SocialMediaLink[] notEqualLinks = new SocialMediaLink[]
        {
            new SocialMediaLink(2L, SocialMedia.ALIGULAC, "url"),
            new SocialMediaLink(1L, SocialMedia.TWITCH, "url")
        };
        TestUtil.testUniqueness(link, equalLink, notEqualLinks);
    }

}
