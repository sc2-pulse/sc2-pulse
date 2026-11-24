// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
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
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.validation.Validator;
import reactor.core.publisher.Flux;

public class MatchServiceTest
{

    @Test
    public void whenArcadeRegion_thenSkipMatches()
    throws Exception
    {
        BlizzardSC2API api = mock(BlizzardSC2API.class);
        when(api.getRequestsPerSecondCap(any())).thenReturn(10f);
        SC2ArcadeLadderService arcade = mock(SC2ArcadeLadderService.class);
        when(arcade.isArcadeRegion(any())).thenAnswer(i->
            Set.of(Region.KR, Region.CN).contains(i.<Region>getArgument(0)));
        UpdateService updateService = mock(UpdateService.class);
        when(updateService.getUpdateContext(any()))
            .thenReturn(new UpdateContext(null, null));
        ConcurrentLinkedQueue<PlayerCharacterNaturalId> requested =
            new ConcurrentLinkedQueue<>();
        //failed and pending characters, EU
        CountDownLatch latch = new CountDownLatch(2);
        when(api.getMatches(any(), any(), anyBoolean(), any()))
            .thenAnswer(i->{
                i.<Iterable<PlayerCharacterNaturalId>>getArgument(0)
                    .forEach(requested::add);
                latch.countDown();
                return Flux.empty();
            });
        EventService eventService = new EventService(10, 10);
        new MatchService
        (
            api,
            mock(PlayerCharacterDAO.class),
            mock(MatchDAO.class),
            mock(MatchParticipantDAO.class),
            mock(SeasonDAO.class),
            mock(SC2MapDAO.class),
            mock(VarDAO.class),
            mock(AlternativeLadderService.class),
            arcade,
            updateService,
            eventService,
            mock(Validator.class),
            new GlobalContext(Set.of(Region.EU, Region.KR))
        );

        PlayerCharacter eu = character(Region.EU);
        PendingLadderData data = new PendingLadderData();
        //CN is not active
        data.getCharacters().addAll(
            Set.of(eu, character(Region.KR), character(Region.CN)));
        eventService.createLadderUpdateEvent(
            new LadderUpdateData(false, List.of(data), List.of()));

        latch.await(10, TimeUnit.SECONDS);
        assertEquals(List.of(eu), List.copyOf(requested));
    }

    private static PlayerCharacter character(Region region)
    {
        return new PlayerCharacter(1L, 1L, region, 1L, 1, "name#1");
    }

}
