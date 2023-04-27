// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.liquipedia;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.nephest.battlenet.sc2.config.AllTestConfig;
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
       api.parsePlayers("Serral", "Maru")
            .toStream()
            .forEach(lpPlayer->
            {
                List<String> links = lpPlayer.getLinks();
                assertNotNull(links);
                assertFalse(links.isEmpty());
            });
    }

}
