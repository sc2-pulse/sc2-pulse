// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.common;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.nephest.battlenet.sc2.config.convert.jackson.LadderTeamStateCollectionToArraySerializer;
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

    @JsonSerialize(using = LadderTeamStateCollectionToArraySerializer.class)
    private final List<LadderTeamState> history;

    @NotNull
    private final List<LadderPlayerCharacterReport> reports;

    public CommonCharacter
    (
        @NotNull List<LadderTeam> teams,
        @NotNull List<LadderDistinctCharacter> linkedDistinctCharacters,
        @NotNull List<LadderPlayerCharacterStats> stats,
        LadderProPlayer proPlayer,
        List<LadderMatch> matches,
        List<LadderTeamState> history,
        List<LadderPlayerCharacterReport> reports
    )
    {
        this.teams = teams;
        this.linkedDistinctCharacters = linkedDistinctCharacters;
        this.stats = stats;
        this.proPlayer = proPlayer;
        this.matches = matches;
        this.history = history;
        this.reports = reports;
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

    public List<LadderPlayerCharacterReport> getReports()
    {
        return reports;
    }

}
