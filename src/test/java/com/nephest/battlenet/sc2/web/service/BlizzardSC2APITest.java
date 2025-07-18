// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.nephest.battlenet.sc2.model.blizzard.BlizzardSeason;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTierDivision;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import jakarta.validation.ValidationException;
import java.text.DecimalFormat;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.RemoveAuthorizedClientOAuth2AuthorizationFailureHandler;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

@ExtendWith(MockitoExtension.class)
public class BlizzardSC2APITest
{


    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager;

    @Mock
    private RemoveAuthorizedClientOAuth2AuthorizationFailureHandler failureHandler;

    @Mock
    private VarDAO varDAO;

    GlobalContext globalContext;

    private BlizzardSC2API api;

    @BeforeEach
    public void beforeEach()
    {
        globalContext = new GlobalContext(Set.of(Region.values()));
        api = new BlizzardSC2API
        (
            objectMapper,
            oAuth2AuthorizedClientManager,
            failureHandler,
            varDAO,
            globalContext,
            false
        );
    }

    @Test
    public void testRequestCapProgress()
    {
        APIHealthMonitor usMonitor = api.getHealthMonitor(Region.US, false);
        APIHealthMonitor euMonitor = api.getHealthMonitor(Region.EU, false);
        APIHealthMonitor krMonitor = api.getHealthMonitor(Region.KR, false);
        APIHealthMonitor krWebMonitor = api.getHealthMonitor(Region.KR, true);
        for(int i = 0; i < BlizzardSC2API.REQUESTS_PER_HOUR_CAP / 4; i++) usMonitor.addRequest();
        for(int i = 0; i < BlizzardSC2API.REQUESTS_PER_HOUR_CAP / 3; i++) euMonitor.addRequest();
        for(int i = 0; i < BlizzardSC2API.REQUESTS_PER_HOUR_CAP / 4; i++) krMonitor.addRequest();
        for(int i = 0; i < BlizzardSC2API.REQUESTS_PER_HOUR_CAP / 4; i++) krWebMonitor.addRequest();

        assertEquals("0.25", new DecimalFormat("#.##").format(api.getRequestCapProgress(Region.US)));
        assertEquals("0.33", new DecimalFormat("#.##").format(api.getRequestCapProgress(Region.EU)));
        assertEquals("0.5", new DecimalFormat("#.##").format(api.getRequestCapProgress(Region.KR)));
        assertEquals("0", new DecimalFormat("#.##").format(api.getRequestCapProgress(Region.CN)));
        assertEquals
        (
            api.isSeparateRequestLimits() ? "0.5" : "1.08",
            new DecimalFormat("#.##").format(api.getRequestCapProgress())
        );
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
        doReturn(fullLadder).when(spy).getLadder(region, division, null);
        doReturn(filteredLadder).when(spy).getFilteredLadder(region, id, seconds, null);

        List<Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>> list
            = List.of(Tuples.of(new BlizzardLeague(), region, new BlizzardLeagueTier(), division));
        spy.getLadders(list, seconds, errors).blockLast();

        verify(spy).getLadders(list, seconds, errors);
        verify(spy).getLadders(list, seconds, errors, null);
        //failed ladder, ignore the timestamp and parse the full ladder
        verify(spy).getLadder(region, division, null);
        verifyNoMoreInteractions(spy);
        assertTrue(errors.get(region).contains(id)); //errors shouldn't be touched/cleared

        errors.get(region).remove(id);
        spy.getLadders(list, seconds, errors).blockLast();

        verify(spy, times(2)).getLadders(list, seconds, errors);
        //normal ladder, conditional pasrsing is in effect
        verify(spy).getFilteredLadder(region, id, seconds, null);
        verify(spy, times(2)).getLadders(list, seconds, errors, null);
        verifyNoMoreInteractions(spy);
    }

    @Test
    public void testOptionalErrorCollection()
    {
        Region region = Region.EU;
        long id = 1L;
        long seconds = 2L;
        Mono<BlizzardLadder> filteredLadder = Mono.error(new IllegalStateException("test"));
        BlizzardTierDivision division = new BlizzardTierDivision(id);
        BlizzardSC2API spy = spy(api);
        doReturn(filteredLadder).when(spy).getFilteredLadder(region, id, seconds, null);

        List<Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>> list
            = List.of(Tuples.of(new BlizzardLeague(), region, new BlizzardLeagueTier(), division));

        spy.getLadders(list, seconds, Map.of()).blockLast();
    }

    @Test
    public void whenRedirectingToHealthyRegion_thenRedirect()
    {
        Region targetRegion = Region.KR;
        api.setForceRegion(targetRegion);
        assertEquals(BlizzardSC2API.DEFAULT_REGION_REDIRECTS.get(targetRegion), api.getForceRegion(targetRegion));
    }

    @Test
    public void whenRedirectingToBrokenRegion_thenPickHealthyRegionInstead()
    {
        Region expectedResult = null;
        Region targetRegion = Region.KR;
        for(Region region : Region.values())
        {
            APIHealthMonitor monitor = api.getHealthMonitor(region, false);
            for(int i = 0; i < 100; i++) monitor.addRequest();
            if(expectedResult == null && region != BlizzardSC2API.DEFAULT_REGION_REDIRECTS.get(targetRegion))
            {
                //healthy API, should be picked
                expectedResult = region;
                //add error to signalize that it's an active region
                monitor.addError();
            }
            else
            {
                //broken API
                for(int i = 0; i < BlizzardSC2API.FORCE_REGION_ERROR_RATE_THRESHOLD + 1; i++)
                    monitor.addError();
            }
            monitor.update();
        }
        api.setForceRegion(targetRegion);
        //default region is ignored because it's broken
        assertNotEquals(BlizzardSC2API.DEFAULT_REGION_REDIRECTS.get(targetRegion), api.getForceRegion(targetRegion));
        //healthy region is picked instead
        assertEquals(expectedResult, api.getForceRegion(targetRegion));
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    public void testCurrentSeasonValidity(boolean isCurrentSeasonValid)
    {
        BlizzardSC2API spy = spy(api);
        OffsetDateTime start = SC2Pulse.offsetDateTime().minusMonths(2);
        OffsetDateTime end = start.plusMonths(1);
        int lastSeasonId = 15;
        BlizzardSeason currentSeason = new BlizzardSeason
        (
            isCurrentSeasonValid ? lastSeasonId : lastSeasonId - 1,
            2020, 1,
            start, end
        );
        BlizzardSeason lastSeason = new BlizzardSeason(lastSeasonId, 2020, 1, start, end);
        doReturn(Mono.just(currentSeason)).when(spy).getCurrentSeason(Region.EU);
        if(!isCurrentSeasonValid)
            doReturn(Mono.just(lastSeason)).when(spy).getLastSeason(Region.EU, lastSeasonId);

        BlizzardSeason result = spy.getCurrentOrLastSeason(Region.EU, lastSeasonId).block();
        assertEquals(isCurrentSeasonValid ? currentSeason : lastSeason, result);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    public void testLastSeasonValidity(boolean isLastSeasonValid)
    {
        BlizzardSC2API spy = spy(api);
        OffsetDateTime start = SC2Pulse.offsetDateTime().minusMonths(2);
        OffsetDateTime end = start.plusMonths(1);
        int lastSeasonId = 15;
        BlizzardSeason currentSeason = new BlizzardSeason
        (
            lastSeasonId - 1,
            2020, 1,
            start, end
        );
        BlizzardSeason lastSeason = new BlizzardSeason
        (
            isLastSeasonValid ? lastSeasonId : lastSeasonId - 1,
            2020, 1,
            start, end
        );
        doReturn(Mono.just(currentSeason)).when(spy).getCurrentSeason(Region.EU);
        doReturn(Mono.just(lastSeason)).when(spy).getLastSeason(Region.EU, lastSeasonId);

        if(isLastSeasonValid)
        {
            assertEquals(lastSeason, spy.getCurrentOrLastSeason(Region.EU, lastSeasonId).block());
        }
        else
        {
            assertThrows
            (
                ValidationException.class,
                ()->spy.getCurrentOrLastSeason(Region.EU, lastSeasonId).block()
            );
        }
    }


}
