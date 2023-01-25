// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLadder;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLeague;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLeagueTier;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTierDivision;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

public class BlizzardSC2APITest
{


    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager;

    @Mock
    private VarDAO varDAO;

    GlobalContext globalContext;

    private BlizzardSC2API api;

    private AutoCloseable mocks;

    @BeforeEach
    public void beforeEach()
    {
        mocks = MockitoAnnotations.openMocks(this);
        globalContext = new GlobalContext(Set.of(Region.values()));
        api = new BlizzardSC2API(objectMapper, oAuth2AuthorizedClientManager, varDAO, globalContext);
    }

    @AfterEach
    public void afterEach()
    throws Exception
    {
        mocks.close();
    }

    @Test
    public void testRequestCapProgress()
    {
        APIHealthMonitor usMonitor = api.getHealthMonitor(Region.US, false);
        APIHealthMonitor euMonitor = api.getHealthMonitor(Region.EU, false);
        APIHealthMonitor krMonitor = api.getHealthMonitor(Region.KR, false);
        for(int i = 0; i < BlizzardSC2API.REQUESTS_PER_HOUR_CAP / 4; i++) usMonitor.addRequest();
        for(int i = 0; i < BlizzardSC2API.REQUESTS_PER_HOUR_CAP / 3; i++) euMonitor.addRequest();
        for(int i = 0; i < BlizzardSC2API.REQUESTS_PER_HOUR_CAP / 2; i++) krMonitor.addRequest();

        assertEquals(0.25, api.getRequestCapProgress(Region.US));
        assertEquals(0.3333333333333333, api.getRequestCapProgress(Region.EU));
        assertEquals(0.5, api.getRequestCapProgress(Region.KR));
        assertEquals(0.0, api.getRequestCapProgress(Region.CN));
        assertEquals(0.5, api.getRequestCapProgress());
    }

    @Test
    public void testConditionalLadderParsing()
    {
        Region region = Region.EU;
        long id = 1L;
        long seconds = 2L;
        Mono<BlizzardLadder> fullLadder = Mono.empty();
        Mono<BlizzardLadder> filteredLadder = Mono.empty();
        BlizzardTierDivision division = new BlizzardTierDivision(id);
        Map<Region, Set<Long>> errors = new EnumMap<>(Region.class);
        errors.put(region, new HashSet<>());
        errors.get(region).add(division.getLadderId());

        BlizzardSC2API spy = spy(api);
        doReturn(fullLadder).when(spy).getLadder(region, division);
        doReturn(filteredLadder).when(spy).getFilteredLadder(region, id, seconds);

        List<Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>> list
            = List.of(Tuples.of(new BlizzardLeague(), region, new BlizzardLeagueTier(), division));
        spy.getLadders(list, seconds, errors).blockLast();

        verify(spy).getLadders(list, seconds, errors);
        //failed ladder, ignore the timestamp and parse the full ladder
        verify(spy).getLadder(region, division);
        verifyNoMoreInteractions(spy);
        assertTrue(errors.get(region).contains(id)); //errors shouldn't be touched/cleared

        errors.get(region).remove(id);
        spy.getLadders(list, seconds, errors).blockLast();

        verify(spy, times(2)).getLadders(list, seconds, errors);
        //normal ladder, conditional pasrsing is in effect
        verify(spy).getFilteredLadder(region, id, seconds);
        verifyNoMoreInteractions(spy);
    }


}
