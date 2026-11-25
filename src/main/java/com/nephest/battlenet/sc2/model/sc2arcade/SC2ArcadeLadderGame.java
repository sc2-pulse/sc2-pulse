// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.sc2arcade;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * There is no reliable match type, it must be inferred.
 */
public record SC2ArcadeLadderGame
(
    @Nullable OffsetDateTime endedAt,
    @Nullable Integer durationSeconds,
    @Nullable GameMap map,
    List<Player> players
)
{

    public record GameMap(@Nullable String name){}

    public record Player
    (
        @Nullable SC2ArcadeLadderCharacter character,
        @Nullable String result
    )
    {}

    public SC2ArcadeLadderGame
    {
        players = players == null ? List.of() : players;
    }

}
