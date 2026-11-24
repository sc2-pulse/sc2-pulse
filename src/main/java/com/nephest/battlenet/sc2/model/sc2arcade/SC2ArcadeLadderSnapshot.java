// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.sc2arcade;

import java.time.OffsetDateTime;
import org.springframework.lang.Nullable;

/**
 * @param season region-local Blizzard season id
 */
public record SC2ArcadeLadderSnapshot
(
    Integer season,
    @Nullable OffsetDateTime observedAt,
    @Nullable OffsetDateTime lastCheckedAt
)
{}
