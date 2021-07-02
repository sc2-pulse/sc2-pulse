// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.common;

import com.nephest.battlenet.sc2.model.local.ladder.*;

import javax.validation.constraints.NotNull;
import java.util.List;

public class CommonCharacter
{

    @NotNull
    private final List<LadderTeam> teams;

    @NotNull
    private final List<LadderDistinctCharacter> linkedDistinctCharacters;

    @NotNull
    private final List<LadderPlayerCharacterStats> stats;

    private final LadderProPlayer proPlayer;

    private final List<LadderMatch> matches;
    
    private final List<LadderTeamState> history;

    public CommonCharacter
    (
        @NotNull List<LadderTeam> teams,
        @NotNull List<LadderDistinctCharacter> linkedDistinctCharacters,
        @NotNull List<LadderPlayerCharacterStats> stats,
        LadderProPlayer proPlayer,
        List<LadderMatch> matches,
        List<LadderTeamState> history
    )
    {
        this.teams = teams;
        this.linkedDistinctCharacters = linkedDistinctCharacters;
        this.stats = stats;
        this.proPlayer = proPlayer;
        this.matches = matches;
        this.history = history;
    }

    public List<LadderTeam> getTeams()
    {
        return teams;
    }

    public List<LadderDistinctCharacter> getLinkedDistinctCharacters()
    {
        return linkedDistinctCharacters;
    }

    public List<LadderPlayerCharacterStats> getStats()
    {
        return stats;
    }

    public LadderProPlayer getProPlayer()
    {
        return proPlayer;
    }

    public List<LadderMatch> getMatches()
    {
        return matches;
    }

    public List<LadderTeamState> getHistory()
    {
        return history;
    }

}
