// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.sc2arcade;

import com.nephest.battlenet.sc2.model.BaseTeam;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import org.springframework.lang.Nullable;

/**
 * @param teamId exceeds signed 64 bits and is shared between queues
 */
public record SC2ArcadeLadderRosterTeam
(
    @NotNull String teamId,
    @Nullable Long mmr,
    @Nullable String race,
    @Nullable Integer wins,
    @Nullable Integer losses,
    @Nullable Double points,
    @NotNull OffsetDateTime joinedAt
)
{

    public BaseTeam toBaseTeam()
    {
        return new BaseTeam
        (
            mmr,
            wins, losses, 0,
            points == null ? null : (int) Math.round(points)
        );
    }

}
