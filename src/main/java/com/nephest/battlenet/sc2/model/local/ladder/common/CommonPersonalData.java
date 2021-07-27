// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.common;

import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.AccountFollowing;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;

import javax.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;

public class CommonPersonalData
{

    @NotNull
    private final Account account;

    @NotNull
    private final Collection<SC2PulseAuthority> roles;

    @NotNull
    private final List<LadderDistinctCharacter> characters;

    @NotNull
    private final List<AccountFollowing> accountFollowings;

    public CommonPersonalData
    (
        Account account,
        Collection<SC2PulseAuthority> roles,
        List<LadderDistinctCharacter> characters,
        List<AccountFollowing> accountFollowings
    )
    {
        this.account = account;
        this.roles = roles;
        this.characters = characters;
        this.accountFollowings = accountFollowings;
    }

    public Account getAccount()
    {
        return account;
    }

    public Collection<SC2PulseAuthority> getRoles()
    {
        return roles;
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
