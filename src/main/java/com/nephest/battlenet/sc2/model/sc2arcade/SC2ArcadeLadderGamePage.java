// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.sc2arcade;

import java.util.List;
import org.springframework.lang.Nullable;

/**
 * @param ready false if the initial backfill of the API is not finished yet
 */
public record SC2ArcadeLadderGamePage
(
    List<SC2ArcadeLadderGameRecord> items,
    @Nullable SC2ArcadeLadderNavigation navigation,
    @Nullable Boolean ready
)
{

    public SC2ArcadeLadderGamePage
    {
        items = items == null ? List.of() : items;
    }

    public boolean hasNext()
    {
        return navigation != null && navigation.hasNext();
    }

    public boolean isReady()
    {
        return !Boolean.FALSE.equals(ready);
    }

}
