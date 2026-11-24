// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.sc2arcade;

import java.time.OffsetDateTime;
import org.springframework.lang.Nullable;

public record SC2ArcadeLadderCoverage(@Nullable Coverage coverage)
{

    public record Coverage
    (
        @Nullable OffsetDateTime oldestStandingsCheckAt,
        @Nullable Integer knownDivisions
    )
    {}

}
