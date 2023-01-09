// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.BaseLocalTeamMember;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class LadderTeamMember
extends BaseLocalTeamMember
implements java.io.Serializable
{

    private static final long serialVersionUID = 5L;

    @NotNull
    private final PlayerCharacter character;

    @NotNull
    private final Account account;

    private final Clan clan;

    private final String proNickname;
    private final String proTeam;
    private final Boolean restrictions;

    public LadderTeamMember
    (
        Account account,
        PlayerCharacter character,
        Clan clan,
        String proNickname,
        String proTeam,
        Boolean restrictions,
        Integer terranGamesPlayed,
        Integer protossGamesPlayed,
        Integer zergGamesPlayed,
        Integer randomGamesPlayed
    )
    {
        super(terranGamesPlayed, protossGamesPlayed, zergGamesPlayed, randomGamesPlayed);
        this.character = character;
        this.account = account;
        this.clan = clan;
        this.proNickname = proNickname;
        this.proTeam = proTeam;
        this.restrictions = restrictions;
    }

    public PlayerCharacter getCharacter()
    {
        return character;
    }

    public Account getAccount()
    {
        return account;
    }

    public Clan getClan()
    {
        return clan;
    }

    public String getProNickname()
    {
        return proNickname;
    }

    public String getProTeam()
    {
        return proTeam;
    }

    public Boolean getRestrictions()
    {
        return restrictions;
    }

}
