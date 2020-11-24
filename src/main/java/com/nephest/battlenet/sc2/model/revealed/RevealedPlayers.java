// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.revealed;

import javax.validation.constraints.NotNull;

public class RevealedPlayers
{

    public static final RevealedProPlayer[] EMPTY_REVEALED_PRO_PLAYER_ARRAY = new RevealedProPlayer[0];

    @NotNull
    private RevealedProPlayer[] players = EMPTY_REVEALED_PRO_PLAYER_ARRAY;

    public RevealedPlayers(){}

    public RevealedPlayers(@NotNull RevealedProPlayer[] players)
    {
        this.players = players;
    }

    public RevealedProPlayer[] getPlayers()
    {
        return players;
    }

    public void setPlayers(RevealedProPlayer[] players)
    {
        this.players = players;
    }

}
