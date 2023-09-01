// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import java.util.Map;
import java.util.Set;

public class MatchUpdateContext
{

    private final Map<Region, Set<PlayerCharacter>> characters;
    private final UpdateContext updateContext;

    public MatchUpdateContext
    (
        Map<Region, Set<PlayerCharacter>> characters,
        UpdateContext updateContext
    )
    {
        this.characters = characters;
        this.updateContext = updateContext;
    }

    public Map<Region, Set<PlayerCharacter>> getCharacters()
    {
        return characters;
    }

    public UpdateContext getUpdateContext()
    {
        return updateContext;
    }

}
