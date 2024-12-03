// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.Region;

public interface StatefulBasicEntityOperations<T>
extends BasicEntityOperations<T>
{

    boolean load(Region region, int season);

    void clear(Region region);

}
