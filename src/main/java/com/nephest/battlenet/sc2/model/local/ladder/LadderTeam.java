// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseTeam;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.LeagueTier;

import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LadderTeam
extends BaseTeam
implements java.io.Serializable
{

    private static final long serialVersionUID = 1l;

    @NotNull
    private final Long id;

    private final LadderSeason season;

    @NotNull
    private final Region region;

    @NotNull
    private final BaseLeague league;

    @NotNull
    private final LeagueTier.LeagueTierType leagueTierType;


    private List<LadderTeamMember> members = Collections.emptyList();

    public LadderTeam
    (
        Long id,
        LadderSeason season,
        Region region,
        BaseLeague league,
        LeagueTier.LeagueTierType leagueTierType,
        List<LadderTeamMember> members,
        Long rating, Integer wins, Integer losses, Integer ties, Integer points
    )
    {
        super(rating, wins, losses, ties, points);
        this.id = id;
        this.season = season;
        this.region = region;
        this.league = league;
        this.leagueTierType = leagueTierType;
        this.members = members;
    }

    public Long getId()
    {
        return id;
    }

    public LadderSeason getSeason()
    {
        return season;
    }

    public Region getRegion()
    {
        return region;
    }

    public BaseLeague getLeague()
    {
        return league;
    }

    public LeagueTier.LeagueTierType getLeagueTierType()
    {
        return leagueTierType;
    }

    public List<LadderTeamMember> getMembers()
    {
        return members;
    }

}

