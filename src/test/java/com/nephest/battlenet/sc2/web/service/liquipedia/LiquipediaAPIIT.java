// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.liquipedia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.liquipedia.LiquipediaPatch;
import com.nephest.battlenet.sc2.model.liquipedia.LiquipediaPlayer;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
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

    @Test
    public void testParsePatchList()
    {
        List<LiquipediaPatch> patches = api.parsePatches().collectList().block();
        assertTrue(patches.size() >= 179);
        Set<String> versions = new HashSet<>();
        for(LiquipediaPatch patch : patches)
        {
            assertTrue(patch.getBuild() > 0);
            assertTrue(versions.add(patch.getVersion()));
            assertFalse(patch.getReleases().isEmpty());
            assertEquals(patch.isBalanceUpdate() ? true : null, patch.isVersus());
        }

        LiquipediaPatch balanceUpdate = patches.stream()
            .filter(LiquipediaPatch::isBalanceUpdate)
            .findFirst()
            .orElseThrow();
        List<LiquipediaPatch> detailsUpdate = patches.stream()
            .filter(patch->!patch.isBalanceUpdate())
            .limit(3)
            .collect(Collectors.toList());
        detailsUpdate.add(balanceUpdate);
        List<LiquipediaPatch> detailsPatches = api.parsePatches(detailsUpdate).collectList().block();
        assertEquals(4, detailsPatches.size());
        detailsUpdate.sort(Comparator.comparing(LiquipediaPatch::getBuild, Comparator.reverseOrder()));
        for(int i = 0; i < detailsPatches.size(); i++)
        {
            LiquipediaPatch detailsPatch = detailsPatches.get(i);
            Assertions.assertThat(detailsUpdate.get(i))
                .usingRecursiveComparison()
                .ignoringFields("releases", "versus")
                .isEqualTo(detailsPatch);
            assertEquals(3, detailsPatch.getReleases().size());
            assertNotNull(detailsPatch.isVersus());
        }
    }

}
