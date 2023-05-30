// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.ClanMemberEvent;
import java.util.List;

public class LadderClanMemberEvents
{

    private final List<LadderDistinctCharacter> characters;
    private final List<Clan> clans;
    private final List<ClanMemberEvent> events;


    public LadderClanMemberEvents
    (
        List<LadderDistinctCharacter> characters,
        List<Clan> clans,
        List<ClanMemberEvent> events
    )
    {
        this.characters = characters;
        this.clans = clans;
        this.events = events;
    }

    public List<LadderDistinctCharacter> getCharacters()
    {
        return characters;
    }

    public List<Clan> getClans()
    {
        return clans;
    }

    public List<ClanMemberEvent> getEvents()
    {
        return events;
    }

}
