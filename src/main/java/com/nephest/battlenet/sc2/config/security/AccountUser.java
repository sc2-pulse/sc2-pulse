// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.model.local.Account;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

public class AccountUser
extends User
{

    private final Account account;

    public AccountUser
    (
        Account account,
        Collection<? extends SC2PulseAuthority> authorities
    )
    {
        super
        (
            String.valueOf(account.getId()),
            //account users don't have a password, using partition + battletag to provide Spring Security with unique,
            //user-controlled string
            account.getPartition().toString() + account.getBattleTag(),
            true, true, true,
            !authorities.contains(SC2PulseAuthority.NONE),
            authorities
        );
        this.account = account;
    }

    public Account getAccount()
    {
        return account;
    }

}
