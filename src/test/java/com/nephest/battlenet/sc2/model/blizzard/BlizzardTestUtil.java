// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

public final class BlizzardTestUtil
{

    public static final BlizzardKey DEFAULT_ACCOUNT_KEY = new BlizzardKey
    (
        """
        https://eu.api.blizzard.com/data/sc2/character\
        /tag-1234\
        /5678\
        ?namespace=prod\
        """
    );

    private BlizzardTestUtil(){}

}
