// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class BlizzardTeamMember
{

    public static final BlizzardTeamMemberRace[] EMPTY_RACE_ARRAY
        = new BlizzardTeamMemberRace[0];

    @Valid @NotNull
    @JsonProperty("legacy_link")
    private BlizzardPlayerCharacter character;

    //races can be empty
    @Valid
    @JsonProperty("played_race_count")
    private BlizzardTeamMemberRace[] races = EMPTY_RACE_ARRAY;

    @Valid @NotNull
    @JsonProperty("character_link")
    private BlizzardAccount account;

    @JsonProperty("clan_link")
    private BlizzardClan clan;

    public BlizzardTeamMember(){}

    public BlizzardTeamMember
    (
        BlizzardPlayerCharacter character,
        BlizzardTeamMemberRace[] races,
        BlizzardAccount account
    )
    {
        this.character = character;
        this.races = races;
        this.account = account;
    }

    public void setCharacter(BlizzardPlayerCharacter character)
    {
        this.character = character;
    }

    public BlizzardPlayerCharacter getCharacter()
    {
        return character;
    }

    public void setRaces(BlizzardTeamMemberRace[] races)
    {
        this.races = races;
    }

    public BlizzardTeamMemberRace[] getRaces()
    {
        return races;
    }

    public void setAccount(BlizzardAccount account)
    {
        this.account = account;
    }

    public BlizzardAccount getAccount()
    {
        return account;
    }

    public BlizzardClan getClan()
    {
        return clan;
    }

    public void setClan(BlizzardClan clan)
    {
        this.clan = clan;
    }

}
