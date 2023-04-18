// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.liquipedia;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.liquipedia.LiquipediaPlayer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class LiquipediaAPIIT
{

    @Autowired
    private LiquipediaAPI api;

    @Test
    public void testParsePlayer()
    {
        LiquipediaPlayer player = api.parsePlayer("Serral").block();
        List<String> links = player.getLinks();
        assertNotNull(links);
        assertFalse(links.isEmpty());
        assertTrue(links.contains("http://aligulac.com/players/485"));
    }

}
