// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PendingLadderData
{

    private final Set<Integer> statsUpdates;
    private final Set<Long> teams;
    private final Set<PlayerCharacter> characters;

    private PendingLadderData
    (
        Set<Integer> statsUpdates,
        Set<Long> teams,
        Set<PlayerCharacter> characters
    )
    {
        this.statsUpdates = statsUpdates;
        this.teams = teams;
        this.characters = characters;
    }


    public PendingLadderData()
    {
        this
        (
            ConcurrentHashMap.newKeySet(),
            ConcurrentHashMap.newKeySet(),
            ConcurrentHashMap.newKeySet()
        );
    }

    public PendingLadderData(PendingLadderData data)
    {
        this();
        this.getStatsUpdates().addAll(data.getStatsUpdates());
        this.getTeams().addAll(data.getTeams());
        this.getCharacters().addAll(data.getCharacters());
    }

    public static PendingLadderData immutableCopy(PendingLadderData data)
    {
        return new PendingLadderData
        (
            Set.copyOf(data.statsUpdates),
            Set.copyOf(data.teams),
            Set.copyOf(data.getCharacters())
        );
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
