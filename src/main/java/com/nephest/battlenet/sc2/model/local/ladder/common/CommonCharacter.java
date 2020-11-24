// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.common;

import com.nephest.battlenet.sc2.model.local.PlayerCharacterStats;
import com.nephest.battlenet.sc2.model.local.ladder.LadderProPlayer;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;

import javax.validation.constraints.NotNull;
import java.util.List;

public class CommonCharacter
{

    @NotNull
    private final List<LadderTeam> teams;

    @NotNull
    private final List<PlayerCharacterStats> stats;

    private final LadderProPlayer proPlayer;

    public CommonCharacter
    (
        @NotNull List<LadderTeam> teams, @NotNull List<PlayerCharacterStats> stats, LadderProPlayer proPlayer
    )
    {
        this.teams = teams;
        this.stats = stats;
        this.proPlayer = proPlayer;
    }

    public List<LadderTeam> getTeams()
    {
        return teams;
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
