// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.Team;
import jakarta.validation.constraints.NotNull;

public class AlternativeTeamData
{

    @NotNull
    private final Account account;

    @NotNull
    private final PlayerCharacter character;

    @NotNull
    private final Team team;

    private final Race race;

    public AlternativeTeamData(Account account, PlayerCharacter character, Team team, Race race)
    {
        this.account = account;
        this.character = character;
        this.team = team;
        this.race = race;
    }

    public Account getAccount()
    {
        return account;
    }

    public PlayerCharacter getCharacter()
    {
        return character;
    }

    public Team getTeam()
    {
        return team;
    }

    public Race getRace()
    {
        return race;
    }

}
