// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.math.BigInteger;
import java.time.Instant;

@JsonNaming(SnakeCaseStrategy.class)
public class BlizzardTeam
extends BlizzardBaseTeam
{

    private static final BlizzardTeamMember[] EMPTY_TEAM_MEMBER_ARRAY = new BlizzardTeamMember[0];

    @NotNull
    private BigInteger id;

    //members can be empty
    @Valid @NotNull
    @JsonProperty("member")
    private BlizzardTeamMember[] members = EMPTY_TEAM_MEMBER_ARRAY;

    @NotNull
    private Instant lastPlayedTimeStamp = Instant.now();

    public BlizzardTeam(){super();}

    public BlizzardTeam
    (
        BigInteger id, BlizzardTeamMember[] members, Instant lastPlayedTimeStamp,
        Long rating, Integer wins, Integer loses, Integer ties, Integer points
    )
    {
        super(rating, wins, loses, ties, points);
        this.id = id;
        this.members = members;
        this.lastPlayedTimeStamp = lastPlayedTimeStamp;
    }

    public void setId(BigInteger id)
    {
        this.id = id;
    }

    public BigInteger getId()
    {
        return id;
    }

    public void setMembers(BlizzardTeamMember[] members)
    {
        this.members = members;
    }

    public BlizzardTeamMember[] getMembers()
    {
        return members;
    }

    public Instant getLastPlayedTimeStamp()
    {
        return lastPlayedTimeStamp;
    }

    public void setLastPlayedTimeStamp(Instant lastPlayedTimeStamp)
    {
        this.lastPlayedTimeStamp = lastPlayedTimeStamp;
    }
}
