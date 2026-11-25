// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.dao.MatchDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchParticipantDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.SC2MapDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.service.EventService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.Validator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MatchServiceTest
{

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    public void testMatchSource(boolean arcadeLadder)
    throws Exception
    {
        BlizzardSC2API api = mock(BlizzardSC2API.class);
        when(api.getRequestsPerSecondCap(any())).thenReturn(10f);
        when(api.isAutoForceRegion()).thenReturn(true);
        //the ladder source must not affect the match source
        Set<Region> ladderRegions = arcadeLadder
            ? Set.of(Region.EU, Region.CN)
            : Set.of(Region.CN);
        SC2ArcadeLadderService arcadeLadderService =
            mock(SC2ArcadeLadderService.class);
        when(arcadeLadderService.getRegions()).thenReturn(ladderRegions);
        when(arcadeLadderService.isArcadeRegion(any())).thenAnswer(i->
            ladderRegions.contains(i.<Region>getArgument(0)));
        Set<Region> matchRegions = Set.of(Region.KR, Region.CN);
        SC2ArcadeMatchService arcadeMatchService = mock(SC2ArcadeMatchService.class);
        when(arcadeMatchService.getRegions()).thenReturn(matchRegions);
        when(arcadeMatchService.isArcadeRegion(any())).thenAnswer(i->
            matchRegions.contains(i.<Region>getArgument(0)));
        PlayerCharacter krCharacter = character(Region.KR);
        when(arcadeMatchService.update()).thenReturn(Mono.just(
            Map.of(Region.KR, Set.of(krCharacter))));
        UpdateService updateService = mock(UpdateService.class);
        when(updateService.getUpdateContext(any()))
            .thenReturn(new UpdateContext(null, null));
        ConcurrentLinkedQueue<PlayerCharacterNaturalId> blizzardRequests =
            new ConcurrentLinkedQueue<>();
        //failed and pending characters, EU
        when(api.getMatches(any(), any(), anyBoolean(), any()))
            .thenAnswer(i->{
                i.<Iterable<PlayerCharacterNaturalId>>getArgument(0)
                    .forEach(blizzardRequests::add);
                return Flux.empty();
            });
        EventService eventService = new EventService(10, 10);
        CompletableFuture<MatchUpdateContext> update =
            new CompletableFuture<>();
        eventService.getMatchUpdateEvent().subscribe(update::complete);
        MatchService matchService = new MatchService
        (
            api,
            mock(PlayerCharacterDAO.class),
            mock(MatchDAO.class),
            mock(MatchParticipantDAO.class),
            mock(SeasonDAO.class),
            mock(SC2MapDAO.class),
            mock(VarDAO.class),
            mock(AlternativeLadderService.class),
            arcadeLadderService,
            arcadeMatchService,
            updateService,
            eventService,
            mock(Validator.class),
            new GlobalContext(Set.of(Region.EU, Region.KR))
        );
        ReflectionTestUtils
            .setField(matchService, "matchService", matchService);

        PlayerCharacter euCharacter = character(Region.EU);
        PendingLadderData data = new PendingLadderData();
        //CN is not active
        data.getCharacters().addAll(
            Set.of(euCharacter, character(Region.KR), character(Region.CN)));
        eventService.createLadderUpdateEvent(
            new LadderUpdateData(false, List.of(data), List.of()));

        MatchUpdateContext context = update.get(10, TimeUnit.SECONDS);
        assertEquals(List.of(euCharacter), List.copyOf(blizzardRequests));
        assertEquals
        (
            Map.of
            (
                Region.EU, Set.of(euCharacter),
                Region.KR, Set.of(krCharacter)
            ),
            context.getCharacters()
        );
        verify(api).setForceRegion(Region.EU);
        verify(api, never()).setForceRegion(Region.KR);
    }

    private static PlayerCharacter character(Region region)
    {
        return new PlayerCharacter(1L, 1L, region, 1L, 1, "name#1");
    }

}
