// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.service;

import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.web.service.LadderUpdateData;
import com.nephest.battlenet.sc2.web.service.MatchUpdateContext;
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
    private final Sinks.Many<LadderUpdateData> ladderUpdateEvent;
    private final Sinks.Many<MatchUpdateContext> matchUpdateEvent;

    @Autowired
    public EventService
    (
        @Value("${com.nephest.battlenet.sc2.event.buffer:#{'6000'}}") int buffer,
        @Value("${com.nephest.battlenet.sc2.event.buffer.small:#{'10'}}") int smallBuffer
    )
    {
        ladderCharacterActivityEvent = Sinks.unsafe()
            .many().multicast().onBackpressureBuffer(buffer);
        ladderUpdateEvent = Sinks.unsafe()
            .many().multicast().onBackpressureBuffer(smallBuffer);
        matchUpdateEvent = Sinks.unsafe()
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

    public synchronized void createLadderUpdateEvent(LadderUpdateData data)
    {
        ladderUpdateEvent.emitNext(data, DEFAULT_FAILURE_HANDLER);
    }

    /**
     * An event is emitted after every ladder update. Emits {@code allStats} flag.
     *
     * @return endless {@link Flux} of ladder updates. Consumers are expected to
     * {@link Flux#subscribe()}.
     */
    public Flux<LadderUpdateData> getLadderUpdateEvent()
    {
        return ladderUpdateEvent.asFlux();
    }

    public synchronized void createMatchUpdateEvent(MatchUpdateContext uc)
    {
        matchUpdateEvent.emitNext(uc, DEFAULT_FAILURE_HANDLER);
    }

    /**
     * An event is emitted when processing of new matches is complete. Emits
     * {@code updateContext} that was used to process matches.
     *
     * @return endless {@link Flux} of match updates. Consumers are expected to
     * {@link Flux#subscribe()}.
     */
    public Flux<MatchUpdateContext> getMatchUpdateEvent()
    {
        return matchUpdateEvent.asFlux();
    }


}
