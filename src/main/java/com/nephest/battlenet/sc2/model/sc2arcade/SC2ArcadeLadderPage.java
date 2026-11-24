// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.sc2arcade;

import java.util.List;
import org.springframework.lang.Nullable;

public record SC2ArcadeLadderPage<T>
(
    List<T> items,
    @Nullable SC2ArcadeLadderNavigation navigation
)
{

    public SC2ArcadeLadderPage
    {
        items = items == null ? List.of() : items;
    }

    public boolean hasNext()
    {
        return navigation != null && navigation.hasNext();
    }

}
