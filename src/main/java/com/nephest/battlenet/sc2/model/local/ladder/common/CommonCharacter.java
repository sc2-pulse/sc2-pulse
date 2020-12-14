// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.common;

import com.nephest.battlenet.sc2.model.local.PlayerCharacterStats;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderProPlayer;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;

import javax.validation.constraints.NotNull;
import java.util.List;

public class CommonCharacter
{

    @NotNull
    private final List<LadderTeam> teams;

    @NotNull
    private final List<LadderDistinctCharacter> linkedDistinctCharacters;

    @NotNull
    private final List<PlayerCharacterStats> stats;

    private final LadderProPlayer proPlayer;

    public CommonCharacter
    (
        @NotNull List<LadderTeam> teams,
        @NotNull List<LadderDistinctCharacter> linkedDistinctCharacters,
        @NotNull List<PlayerCharacterStats> stats,
        LadderProPlayer proPlayer
    )
    {
        this.teams = teams;
        this.linkedDistinctCharacters = linkedDistinctCharacters;
        this.stats = stats;
        this.proPlayer = proPlayer;
    }

    public List<LadderTeam> getTeams()
    {
        return teams;
    }

    public List<LadderDistinctCharacter> getLinkedDistinctCharacters()
    {
        return linkedDistinctCharacters;
    }

    public List<PlayerCharacterStats> getStats()
    {
        return stats;
    }

    public LadderProPlayer getProPlayer()
    {
        return proPlayer;
    }

}
