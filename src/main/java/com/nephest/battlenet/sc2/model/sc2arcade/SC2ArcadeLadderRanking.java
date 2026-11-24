// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.sc2arcade;

import java.util.List;

public record SC2ArcadeLadderRanking
(
    String teamId,
    List<SC2ArcadeLadderCharacter> members
)
{

    public SC2ArcadeLadderRanking
    {
        members = members == null ? List.of() : members;
    }

}
