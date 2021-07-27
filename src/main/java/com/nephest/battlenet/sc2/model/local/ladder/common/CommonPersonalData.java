// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.common;

import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.AccountFollowing;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;

import javax.validation.constraints.NotNull;
import java.util.List;

public class CommonPersonalData
{

    @NotNull
    private final Account account;

    @NotNull
    private final List<LadderDistinctCharacter> characters;

    @NotNull
    private final List<AccountFollowing> accountFollowings;

    public CommonPersonalData
    (Account account, List<LadderDistinctCharacter> characters, List<AccountFollowing> accountFollowings)
    {
        this.account = account;
        this.characters = characters;
        this.accountFollowings = accountFollowings;
    }

    public Account getAccount()
    {
        return account;
    }

    public List<LadderDistinctCharacter> getCharacters()
    {
        return characters;
    }

    public List<AccountFollowing> getAccountFollowings()
    {
        return accountFollowings;
    }

}
