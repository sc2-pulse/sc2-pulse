// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.liquipedia;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.liquipedia.LiquipediaPatch;
import com.nephest.battlenet.sc2.model.liquipedia.LiquipediaPlayer;
import com.nephest.battlenet.sc2.model.liquipedia.query.revision.LiquipediaMediaWikiRevisionQueryResult;
import com.nephest.battlenet.sc2.util.TestUtil;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
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
            "liquipedia/liquipedia-query.json",
            LiquipediaMediaWikiRevisionQueryResult.class
        );
        List<LiquipediaPlayer> players = LiquipediaParser.parse(result);
        assertEquals(4, players.size());
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
    }

    @Test
    public void testRedirect()
    throws URISyntaxException, IOException
    {
        LiquipediaMediaWikiRevisionQueryResult result = TestUtil.readResource
        (
            LiquipediaParserTest.class,
            "liquipedia/liquipedia-query-redirect.json",
            LiquipediaMediaWikiRevisionQueryResult.class
        );
        List<LiquipediaPlayer> players = LiquipediaParser.parse(result);
        assertEquals(1, players.size());
        LiquipediaPlayer demuslim = players.get(0);
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
            "liquipedia/liquipedia-query-empty-values.json",
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

    @Test
    public void whenRedundantLinkSlash_thenRemoveIt()
    throws URISyntaxException, IOException
    {
        LiquipediaMediaWikiRevisionQueryResult result = TestUtil.readResource
        (
            LiquipediaParserTest.class,
            "liquipedia/liquipedia-query-redundant-slash.json",
            LiquipediaMediaWikiRevisionQueryResult.class
        );
        List<LiquipediaPlayer> players = LiquipediaParser.parse(result);
        assertEquals(1, players.size());
        List<String> links = players.get(0).getLinks();
        links.sort(Comparator.naturalOrder());
        assertEquals(3, links.size());
        //redundant slash is removed(//channel/UCOlVDgCRQjAkdIsYnfi199Q)
        assertEquals("https://www.youtube.com/channel/UCOlVDgCRQjAkdIsYnfi199Q", links.get(2));
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

    @CsvSource
    ({
        "'\n url\n', 'url'",
        "'\n /url \n', 'url'"
    })
    @ParameterizedTest
    public void testSanitizeUserUrl(String input, String expectedResult)
    {
        assertEquals(expectedResult, LiquipediaParser.sanitizeUserUrl(input));
    }

    @Test
    public void testParsePatchList()
    throws URISyntaxException, IOException
    {
        LiquipediaMediaWikiRevisionQueryResult result = TestUtil.readResource
        (
            LiquipediaParserTest.class,
            "liquipedia/liquipedia-query-patches.json",
            LiquipediaMediaWikiRevisionQueryResult.class
        );

        List<LiquipediaPatch> patches = LiquipediaParser.parsePatchList(result);
        assertEquals(181, patches.size());

        Assertions.assertThat(patches.get(2))
            .usingRecursiveComparison()
            .isEqualTo(new LiquipediaPatch(
                91115L,
                "5.0.12",
                Map.of(Region.US, LocalDate.of(2023, 9, 29)),
                null
            ));
        Assertions.assertThat(patches.get(180))
            .usingRecursiveComparison()
            .isEqualTo(new LiquipediaPatch(
                16195L,
                "1.0.1",
                Map.of(Region.US, LocalDate.of(2010, 7, 30)),
                null
            ));
        Assertions.assertThat(patches.get(12))
            .usingRecursiveComparison()
            .isEqualTo(new LiquipediaPatch(
                81102L,
                "5.0.2 BU",
                Map.of
                (
                    Region.US, LocalDate.of(2020, 8, 20),
                    Region.EU, LocalDate.of(2020, 8, 20),
                    Region.KR, LocalDate.of(2020, 8, 20)
                ),
                true
            ));
    }

    @Test
    public void testParsePatchDetails()
    throws URISyntaxException, IOException
    {
        LiquipediaMediaWikiRevisionQueryResult result = TestUtil.readResource
        (
            LiquipediaParserTest.class,
            "liquipedia/liquipedia-query-patches-details.json",
            LiquipediaMediaWikiRevisionQueryResult.class
        );
        List<LiquipediaPatch> patches = LiquipediaParser.parsePatches(result);
        patches.sort(Comparator.comparing(LiquipediaPatch::getBuild,
            Comparator.nullsFirst(Comparator.reverseOrder())));
        Assertions.assertThat(patches)
            .usingRecursiveComparison()
            .isEqualTo(List.of(
                new LiquipediaPatch
                (
                    null,
                    "5.0.12",
                    Map.of
                    (
                        Region.US, LocalDate.of(2023, 9, 29),
                        Region.EU, LocalDate.of(2023, 10, 2),
                        Region.KR, LocalDate.of(2023, 10, 2)
                    ),
                    true
                ),
                new LiquipediaPatch
                (
                    88500L,
                    "5.0.10",
                    Map.of
                    (
                        Region.US, LocalDate.of(2022, 7, 20),
                        Region.EU, LocalDate.of(2022, 7, 20),
                        Region.KR, LocalDate.of(2022, 7, 21)
                    ),
                    true
                ),
                new LiquipediaPatch
                (
                    83830L,
                    "5.0.6",
                    Map.of
                    (
                        Region.US, LocalDate.of(2021, 2, 2),
                        Region.EU, LocalDate.of(2021, 2, 3),
                        Region.KR, LocalDate.of(2021, 2, 3)
                    ),
                    false
                ),
                new LiquipediaPatch
                (
                    81102L,
                    "5.0.2",
                    Map.of
                    (
                        Region.US, LocalDate.of(2020, 8, 6),
                        Region.EU, LocalDate.of(2020, 8, 6),
                        Region.KR, LocalDate.of(2020, 8, 7)
                    ),
                    false
                )
            ));
    }

}
