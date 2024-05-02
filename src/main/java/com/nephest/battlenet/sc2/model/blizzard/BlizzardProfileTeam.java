// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class BlizzardProfileTeam
extends BlizzardBaseTeam
{

    public static final BlizzardProfileTeamMember[] EMPTY_TEAM_MEMBER_ARRAY = new BlizzardProfileTeamMember[0];

    @Valid @NotNull
    private BlizzardProfileTeamMember[] teamMembers = EMPTY_TEAM_MEMBER_ARRAY;

    public BlizzardProfileTeam(){}

    public BlizzardProfileTeam
    (
        @Valid @NotNull BlizzardProfileTeamMember[] members,
        Long rating, Integer wins, Integer loses, Integer ties, Integer points
    )
    {
        super(rating, wins, loses, ties, points);
        this.teamMembers = members;
    }

    public BlizzardProfileTeamMember[] getTeamMembers()
    {
        return teamMembers;
    }

    public void setTeamMembers(@Valid @NotNull BlizzardProfileTeamMember[] teamMembers)
    {
        this.teamMembers = teamMembers;
    }

    @Override
    @JsonAlias("mmr")
    public void setRating(Long rating)
    {
        super.setRating(rating);
    }

}
