// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.liquipedia;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.model.liquipedia.LiquipediaPlayer;
import com.nephest.battlenet.sc2.model.liquipedia.query.revision.LiquipediaMediaWikiRevisionQueryResult;
import com.nephest.battlenet.sc2.util.TestUtil;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class LiquipediaParserTest
{

    @Test
    public void testParsePlayer()
    throws URISyntaxException, IOException
    {
        LiquipediaMediaWikiRevisionQueryResult result = TestUtil.readResource
        (
            LiquipediaParserTest.class,
            "liquipedia-query.json",
            LiquipediaMediaWikiRevisionQueryResult.class
        );
        List<LiquipediaPlayer> players = LiquipediaParser.parse(result);
        assertEquals(5, players.size());
        List<String> serralLinks = players.stream()
            .filter(player->player.getName().equalsIgnoreCase("Serral"))
            .findAny()
            .map(LiquipediaPlayer::getLinks)
            .orElseThrow();
        serralLinks.sort(Comparator.naturalOrder());
        assertEquals(3, serralLinks.size());
        assertEquals("https://discord.gg/GrnX3jUtsA", serralLinks.get(0));
        assertEquals("https://twitter.com/Serral_SC2", serralLinks.get(1));
        assertEquals("https://www.twitch.tv/serral", serralLinks.get(2));

        List<String> maruLinks = players.stream()
            .filter(p->p.getName().equalsIgnoreCase("Maru"))
            .findAny()
            .map(LiquipediaPlayer::getLinks)
            .orElseThrow();
        maruLinks.sort(Comparator.naturalOrder());
        assertEquals(2, maruLinks.size());
        assertEquals("https://www.instagram.com/maru00072", maruLinks.get(0));
        assertEquals("https://www.twitch.tv/maru072", maruLinks.get(1));

        List<String> harstemLinks = players.stream()
            .filter(p->p.getName().equalsIgnoreCase("Harstem"))
            .findAny()
            .map(LiquipediaPlayer::getLinks)
            .orElseThrow();
        harstemLinks.sort(Comparator.naturalOrder());
        assertEquals(4, harstemLinks.size());
        assertEquals("https://twitter.com/HarstemSc2", harstemLinks.get(0));
        assertEquals("https://www.instagram.com/harstemsc", harstemLinks.get(1));
        assertEquals("https://www.twitch.tv/harstem", harstemLinks.get(2));
        assertEquals("https://www.youtube.com/channel/UCCRdB9rqzP2m7bPYb5drH_Q", harstemLinks.get(3));

        LiquipediaPlayer jEcho = players.stream()
            .filter(p->p.getName().equalsIgnoreCase("JEcho"))
            .findAny()
            .orElseThrow();
        assertEquals("jEcho", jEcho.getQueryName());
        assertEquals("JEcho", jEcho.getName());

        LiquipediaPlayer demuslim = players.stream()
            .filter(p->p.getName().equalsIgnoreCase("DeMusliM"))
            .findAny()
            .orElseThrow();
        assertEquals("DeMusliM", demuslim.getQueryName());
        assertEquals("DeMusliM", demuslim.getName());
        assertEquals("DeMu", demuslim.getRedirect());
    }

    @Test
    public void whenEmptyValues_thenSkipThem()
    throws URISyntaxException, IOException
    {
        LiquipediaMediaWikiRevisionQueryResult result = TestUtil.readResource
        (
            LiquipediaParserTest.class,
            "liquipedia-query-empty-values.json",
            LiquipediaMediaWikiRevisionQueryResult.class
        );
        List<LiquipediaPlayer> players = LiquipediaParser.parse(result);
        assertEquals(1, players.size());
        List<String> links = players.get(0).getLinks();
        links.sort(Comparator.naturalOrder());
        //empty values(youtube=) are ignored
        assertEquals(2, links.size());
        assertEquals("https://twitter.com/ibabattlenet", links.get(0));
        assertEquals("https://www.twitch.tv/Iba_sc2", links.get(1));
    }

    @CsvSource
    ({
        "'\nhttps://web.archive.org/web/20230331032550/http://aligulac.com/players/49-Maru/ \n', "
            + "'http://aligulac.com/players/49-Maru/'",
        "'\nhttps://web.archive.org/web/20230331032550/https://aligulac.com/players/49-Maru/ \n', "
            + "'https://aligulac.com/players/49-Maru/'",
        "'http://aligulac.com/players/49-Maru/', 'http://aligulac.com/players/49-Maru/'"
    })
    @ParameterizedTest
    public void testSanitizeUrl(String input, String expectedResult)
    {
        assertEquals(expectedResult, LiquipediaParser.sanitizeUrl(input));
    }

}
