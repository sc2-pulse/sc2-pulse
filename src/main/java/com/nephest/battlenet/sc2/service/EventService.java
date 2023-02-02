// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.service;

import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.util.ProgrammaticFlux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class EventService
{

    private static final Logger LOG = LoggerFactory.getLogger(EventService.class);

    private final ProgrammaticFlux<PlayerCharacter> ladderCharacterActivityEvent;

    @Autowired
    public EventService()
    {
        ladderCharacterActivityEvent =
            new ProgrammaticFlux<>(c->LOG.trace("Ladder character activity event: {}", c));
    }

    public void createLadderCharacterActivityEvent(PlayerCharacter playerCharacter)
    {
        ladderCharacterActivityEvent.getSink().next(playerCharacter);
    }

    /**
     * An event is emitted each time a {@link PlayerCharacter} is seen active on the ladder.
     *
     * @return endless {@link Flux} of active characters. Consumers are expected to
     * {@link Flux#subscribe()}.
     */
    public Flux<PlayerCharacter> getLadderCharacterActivityEvent()
    {
        return ladderCharacterActivityEvent.getFlux();
    }


}
