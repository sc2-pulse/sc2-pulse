// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PendingLadderData
{

    private final Set<Integer> statsUpdates = ConcurrentHashMap.newKeySet();
    private final Set<Long> teams = ConcurrentHashMap.newKeySet();
    private final Set<PlayerCharacter> characters = ConcurrentHashMap.newKeySet();

    public PendingLadderData()
    {
    }

    public PendingLadderData(PendingLadderData data)
    {
        this.getStatsUpdates().addAll(data.getStatsUpdates());
        this.getTeams().addAll(data.getTeams());
        this.getCharacters().addAll(data.getCharacters());
    }

    public void clear()
    {
        statsUpdates.clear();
        teams.clear();
        characters.clear();
    }

    public Set<Integer> getStatsUpdates()
    {
        return statsUpdates;
    }

    public Set<Long> getTeams()
    {
        return teams;
    }

    public Set<PlayerCharacter> getCharacters()
    {
        return characters;
    }

}
