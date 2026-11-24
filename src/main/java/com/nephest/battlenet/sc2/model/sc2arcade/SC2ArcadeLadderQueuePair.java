// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.sc2arcade;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.TeamType;

public record SC2ArcadeLadderQueuePair(QueueType queueType, TeamType teamType)
{

    public String queueAlias()
    {
        return queueType.getTeamFormat().getName();
    }

    public String teamTypeAlias()
    {
        return teamType.name();
    }

}
