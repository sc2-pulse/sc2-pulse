// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.blizzard;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLeague;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardMatch;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardSeason;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTierDivision;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.web.service.WebServiceTestUtil;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class BlizzardSC2APIIT
{

    public static final int RETRY_COUNT = 2;
    public static final String VALID_SEASON = "{\"seasonId\": 1, \"year\": 2010, \"number\": 1}";
    public static final String VALID_LEAGUE = "{\"type\": 0, \"queueType\": 201, \"teamType\": 0, \"tier\": []}";
    public static final String VALID_LADDER = "{\"team\": []}";
    public static final String VALID_MATCHES = "{\"matches\": []}";

    public static final PlayerCharacter SERRAL =
        new PlayerCharacter(null, null, Region.EU, 315071L, 1, "Serral");
    public static final PlayerCharacter HEROMARINE =
        new PlayerCharacter(null, null, Region.EU, 7069585L, 1, "Heromarine");
    public static final PlayerCharacter MARU =
        new PlayerCharacter(null, null, Region.KR, 4582362L, 1, "Maru");
    @Autowired
    private BlizzardSC2API api;

    @Autowired
    private Validator validator;

    @Test @Order(1)
    public void testFetch()
    {
        BlizzardSeason season = api.getCurrentSeason(Region.EU).block();
        assertNotNull(season);
        assertNotNull(season.getId());
        assertNotNull(season.getNumber());
        assertNotNull(season.getYear());
        assertNotNull(season.getStart());
        assertNotNull(season.getEnd());
    }

    @Test @Order(2)
    public void testFetchMatches()
    {
        api.getMatches(Set.of(SERRAL, HEROMARINE, MARU))
            .sequential()
            .doOnNext((m)->
            {
                assertTrue(m.getT1().getMatches().length > 0);
                for(BlizzardMatch match : m.getT1().getMatches())
                {
                    Errors errors = new BeanPropertyBindingResult(match, match.toString());
                    validator.validate(match, errors);
                    assertFalse(errors.hasErrors());
                }
            }).blockLast();
    }

    @Test @Order(3)
    public void testRetrying()
    throws Exception
    {
        MockWebServer server = new MockWebServer();
        server.start();
        api.setRegionUri(server.url("/someurl").uri().toString());
        api.setWebClient(WebServiceTestUtil.createTimeoutClient());

        WebServiceTestUtil.testRetrying(api.getCurrentSeason(Region.EU), VALID_SEASON, server, RETRY_COUNT);
        WebServiceTestUtil.testRetrying
        (
            api.getLeague
            (
                Region.EU,
                mock(BlizzardSeason.class),
                BlizzardLeague.LeagueType.BRONZE,
                QueueType.LOTV_1V1,
                TeamType.ARRANGED
            ),
            VALID_LEAGUE, server, RETRY_COUNT
        );
        WebServiceTestUtil.testRetrying(api.getLadder(Region.EU, mock(BlizzardTierDivision.class)), VALID_LADDER, server, RETRY_COUNT);
        WebServiceTestUtil.testRetrying(api.getMatches(SERRAL), VALID_MATCHES, server, RETRY_COUNT);
        server.shutdown();
    }

}
