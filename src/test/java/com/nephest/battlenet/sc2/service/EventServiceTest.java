// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.service;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

public class EventServiceTest
{

    private EventService eventService;

    @BeforeEach
    public void beforeEach()
    {
        eventService = new EventService(2, 1);
    }

    @Test
    public void testCharacterQueue() throws InterruptedException
    {
        PlayerCharacter char1 = new PlayerCharacter(1L, 1L, Region.EU, 1L, 1, "name#1");
        PlayerCharacter char2 = new PlayerCharacter(2L, 2L, Region.EU, 2L, 2, "name#2");
        eventService.createLadderCharacterActivityEvent(char1);
        eventService.createLadderCharacterActivityEvent(char2);

        StepVerifier.create(eventService.getLadderCharacterActivityEvent())
            .expectNext(char1)
            .expectNext(char2)
            .expectNoEvent(Duration.ofMillis(1));
    }

}
