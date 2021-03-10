// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.LeagueTier;
import com.nephest.battlenet.sc2.model.local.Team;

import java.math.BigInteger;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LadderTeam
extends Team
implements java.io.Serializable
{

    private static final long serialVersionUID = 3L;

    private final List<LadderTeamMember> members;

    public LadderTeam
    (
        Long id,
        Integer season,
        Region region,
        BaseLeague league,
        LeagueTier.LeagueTierType leagueTierType,
        Integer divisionId,
        BigInteger battlenetId,
        Long rating, Integer wins, Integer losses, Integer ties, Integer points,
        List<LadderTeamMember> members
    )
    {
        super(id, season, region, league, leagueTierType, divisionId, battlenetId, rating, wins, losses, ties,points);
        this.members = members;
    }

    public List<LadderTeamMember> getMembers()
    {
        return members;
    }

}

