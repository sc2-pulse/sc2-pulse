// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.model.local.Account;
import java.util.Collection;
import org.springframework.security.core.userdetails.User;

public class AccountUser
extends User
{

    private final Account account;

    public AccountUser
    (
        Account account,
        String password,
        Collection<? extends SC2PulseAuthority> authorities
    )
    {
        super
        (
            String.valueOf(account.getId()),
            password,
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
