// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.liquipedia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.liquipedia.LiquipediaPlayer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        Set<String> names = Set.of("Serral", "Maru", "Harstem", "DeMusliM");
        Set<String> urls = new HashSet<>();
        List<LiquipediaPlayer> players = api.parsePlayers(names)
            .collectList()
            .block();
        assertEquals(names.size(), players.size());
        players.forEach(lpPlayer->
        {
            assertTrue(names.contains(lpPlayer.getQueryName()));
            List<String> links = lpPlayer.getLinks();
            assertNotNull(links);
            assertFalse(links.isEmpty());
            //verify unique links
            links.forEach(link->assertFalse(urls.contains(link)));
            urls.addAll(links);
        });
    }

}
