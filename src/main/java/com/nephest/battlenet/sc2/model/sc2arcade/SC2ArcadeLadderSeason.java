// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.sc2arcade;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Season;
import java.time.OffsetDateTime;

/**
 * @param season region-local Blizzard season id
 */
public record SC2ArcadeLadderSeason
(
    Integer season,
    Integer year,
    Integer number,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt
)
{

    public Season toSeason(Region region)
    {
        return new Season(null, season, region, year, number, startsAt, endsAt);
    }

}
