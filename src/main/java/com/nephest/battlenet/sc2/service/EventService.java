// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.service;

import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Service
public class EventService
{

    public static final Sinks.EmitFailureHandler DEFAULT_FAILURE_HANDLER =
        Sinks.EmitFailureHandler.FAIL_FAST;

    private final Sinks.Many<PlayerCharacter> ladderCharacterActivityEvent;
    private final Sinks.Many<Boolean> ladderUpdateEvent;

    @Autowired
    public EventService
    (
        @Value("${com.nephest.battlenet.sc2.event.buffer:#{'5000'}}") int buffer,
        @Value("${com.nephest.battlenet.sc2.event.buffer.small:#{'10'}}") int smallBuffer
    )
    {
        ladderCharacterActivityEvent = Sinks.unsafe()
            .many().multicast().onBackpressureBuffer(buffer);
        ladderUpdateEvent = Sinks.unsafe()
            .many().multicast().onBackpressureBuffer(smallBuffer);
    }

    public synchronized void createLadderCharacterActivityEvent(PlayerCharacter... playerCharacters)
    {
        for(PlayerCharacter playerCharacter : playerCharacters)
            ladderCharacterActivityEvent.emitNext(playerCharacter, DEFAULT_FAILURE_HANDLER);
    }

    /**
     * An event is emitted each time a {@link PlayerCharacter} is seen active on the ladder.
     *
     * @return endless {@link Flux} of active characters. Consumers are expected to
     * {@link Flux#subscribe()}.
     */
    public Flux<PlayerCharacter> getLadderCharacterActivityEvent()
    {
        return ladderCharacterActivityEvent.asFlux();
    }

    public synchronized void createLadderUpdateEvent(Boolean allStats)
    {
        ladderUpdateEvent.emitNext(allStats, DEFAULT_FAILURE_HANDLER);
    }

    /**
     * An event is emitted after every ladder update. Emits {@code allStats} flag.
     *
     * @return endless {@link Flux} of ladder updates. Consumers are expected to
     * {@link Flux#subscribe()}.
     */
    public Flux<Boolean> getLadderUpdateEvent()
    {
        return ladderUpdateEvent.asFlux();
    }


}
