// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderProPlayer;
import java.util.List;

public class Group
{

    private final List<LadderDistinctCharacter> characters;
    private final List<Clan> clans;
    private final List<LadderProPlayer> proPlayers;

    public Group
    (
        List<LadderDistinctCharacter> characters,
        List<Clan> clans,
        List<LadderProPlayer> proPlayers
    )
    {
        this.characters = characters;
        this.clans = clans;
        this.proPlayers = proPlayers;
    }

    public List<LadderDistinctCharacter> getCharacters()
    {
        return characters;
    }

    public List<Clan> getClans()
    {
        return clans;
    }

    public List<LadderProPlayer> getProPlayers()
    {
        return proPlayers;
    }

}
