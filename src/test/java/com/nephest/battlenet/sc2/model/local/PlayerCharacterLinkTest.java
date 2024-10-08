// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.util.TestUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class PlayerCharacterLinkTest
{

    @Test
    public void testUniqueness()
    {
        PlayerCharacterLink link = new PlayerCharacterLink(1L, SocialMedia.YOUTUBE, "url1");
        PlayerCharacterLink equalLink = new PlayerCharacterLink(1L, SocialMedia.YOUTUBE, "url2");
        PlayerCharacterLink[] notEqualLinks = new PlayerCharacterLink[]
        {
            new PlayerCharacterLink(2L, SocialMedia.YOUTUBE, "url1"),
            new PlayerCharacterLink(1L, SocialMedia.INSTAGRAM, "url1")
        };

        TestUtil.testUniqueness(link, equalLink, (Object[]) notEqualLinks);
    }

    @CsvSource
    ({
        "ALIGULAC, 123, http://aligulac.com/players/123",
        "UNKNOWN, 123, 123"
    })
    @ParameterizedTest
    public void testGetAbsoluteUrl(SocialMedia socialMedia, String relativeUrl, String absoluteUrl)
    {
        PlayerCharacterLink link = new PlayerCharacterLink(0L, socialMedia, relativeUrl);
        assertEquals(absoluteUrl, link.getAbsoluteUrl());
    }

    @CsvSource
    ({
        "http://aligulac.com/players/123, 123",
        "/123, /123",
        "123, 123",
        ",",
        "http://aligulac.com/players/,",
        "battlenet:://starcraft/profile/1/2, 1/2",
        "battlenet://starcraft/profile/1/2, 1/2",
        "starcraft://profile/2/3, 2/3"
    })
    @ParameterizedTest
    public void testGetRelativeUrl(String absoluteUrl, String expectedResult)
    {
        if(expectedResult == null)
        {
            assertTrue(()->PlayerCharacterLink.getRelativeUrl(absoluteUrl).isEmpty());
        }
        else
        {
            assertEquals(expectedResult, PlayerCharacterLink.getRelativeUrl(absoluteUrl).orElseThrow());
        }
    }

    @Test
    public void testSetAbsoluteUrl()
    {
        PlayerCharacterLink link = new PlayerCharacterLink(1L, SocialMedia.YOUTUBE, "url1");
        link.setAbsoluteUrl("http://aligulac.com/players/123");
        assertEquals(1L, link.getPlayerCharacterId());
        assertEquals(SocialMedia.ALIGULAC, link.getType());
        assertEquals("123", link.getRelativeUrl());
        assertEquals("http://aligulac.com/players/123", link.getAbsoluteUrl());
    }

}
