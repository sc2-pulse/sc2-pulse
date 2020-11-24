// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.BaseLocalTeamMember;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;

import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LadderTeamMember
extends BaseLocalTeamMember
implements java.io.Serializable
{

    private static final long serialVersionUID = 4L;

    @NotNull
    private final PlayerCharacter character;

    @NotNull
    private final Account account;

    private final String proNickname;
    private final String proTeam;

    public LadderTeamMember
    (
        Account account,
        PlayerCharacter character,
        String proNickname,
        String proTeam,
        Integer terranGamesPlayed,
        Integer protossGamesPlayed,
        Integer zergGamesPlayed,
        Integer randomGamesPlayed
    )
    {
        super(terranGamesPlayed, protossGamesPlayed, zergGamesPlayed, randomGamesPlayed);
        this.character = character;
        this.account = account;
        this.proNickname = proNickname;
        this.proTeam = proTeam;
    }

    public PlayerCharacter getCharacter()
    {
        return character;
    }

    public Account getAccount()
    {
        return account;
    }

    public String getProNickname()
    {
        return proNickname;
    }

    public String getProTeam()
    {
        return proTeam;
    }

}
