// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderPlayerSearchStats;

public final class DiscordTestUtil
{

    private DiscordTestUtil(){}

    public static LadderDistinctCharacter createSimpleCharacter
    (
        String battleTag,
        String name,
        String proName,
        String clan,
        String proTeam,
        Region region,
        int rating,
        long id
    )
    {
        return new LadderDistinctCharacter
        (
            BaseLeague.LeagueType.GRANDMASTER,
            0,
            new Account(id, Partition.GLOBAL, battleTag),
            new PlayerCharacter(id, id, region, id, 0, name),
            clan != null ? new Clan((int) id, clan, region, clan) : null,
            proName,
            proTeam,
            null,
            0, 0, 0, 0, 0,
            null,
            new LadderPlayerSearchStats(rating, 0, 0)
        );
    }

}
