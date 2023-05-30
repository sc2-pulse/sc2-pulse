// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import java.util.List;

public class Group
{

    private final List<LadderDistinctCharacter> characters;
    private final List<Clan> clans;

    public Group(List<LadderDistinctCharacter> characters, List<Clan> clans)
    {
        this.characters = characters;
        this.clans = clans;
    }

    public List<LadderDistinctCharacter> getCharacters()
    {
        return characters;
    }

    public List<Clan> getClans()
    {
        return clans;
    }

}
