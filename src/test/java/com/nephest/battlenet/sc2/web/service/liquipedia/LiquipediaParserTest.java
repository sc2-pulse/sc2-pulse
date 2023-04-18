// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.liquipedia;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.liquipedia.LiquipediaMediaWikiParseResult;
import com.nephest.battlenet.sc2.model.liquipedia.LiquipediaPlayer;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
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
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        String parseText = Files.readString
        (
            Paths.get(LiquipediaParserTest.class.getResource("liquipedia-parse.json").toURI()),
            Charset.defaultCharset()
        );
        String text = objectMapper
            .readValue(parseText, LiquipediaMediaWikiParseResult.class)
            .getParse()
            .getText()
            .getValue();
        LiquipediaPlayer player = LiquipediaParser.parsePlayer(text);
        List<String> links = player.getLinks();
        assertEquals(7, links.size());
        assertEquals("http://aligulac.com/players/485", links.get(0));
        assertEquals("https://challonge.com/users/serral", links.get(1));
        assertEquals("https://play.eslgaming.com/player/6467940", links.get(2));
        assertEquals("https://discord.gg/GrnX3jUtsA", links.get(3));
        assertEquals("https://twitter.com/Serral_SC2", links.get(4));
        assertEquals("https://www.twitch.tv/serral", links.get(5));
        assertEquals("https://tl.net/forum/fan-clubs/448777-serral-fanclub", links.get(6));
    }

    @CsvSource
    ({
        "'\nhttps://web.archive.org/web/20230331032550/http://aligulac.com/players/49-Maru/ \n', "
            + "'http://aligulac.com/players/49-Maru/'",
        "'http://aligulac.com/players/49-Maru/', 'http://aligulac.com/players/49-Maru/'"
    })
    @ParameterizedTest
    public void testSanitizeUrl(String input, String expectedResult)
    {
        assertEquals(expectedResult, LiquipediaParser.sanitizeUrl(input));
    }

}
