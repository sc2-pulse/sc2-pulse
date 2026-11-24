// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.sc2arcade;

import org.springframework.lang.Nullable;

public record SC2ArcadeLadderNavigation(@Nullable String next, Boolean hasMore)
{

    public boolean hasNext()
    {
        return Boolean.TRUE.equals(hasMore) && next != null;
    }

}
