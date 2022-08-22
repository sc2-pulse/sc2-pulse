// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.model.local.Account;
import java.util.Collection;
import java.util.Map;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class AccountOauth2User<U extends OAuth2User>
extends AccountUser
implements OAuth2User
{

    private final U user;

    public AccountOauth2User(U user, Account account, Collection<? extends SC2PulseAuthority> authorities)
    {
        super(account, authorities);
        this.user = user;
    }

    @Override
    public String toString()
    {
        return String.valueOf(getAccount().getId());
    }

    @Override
    public Map<String, Object> getAttributes()
    {
        return user.getAttributes();
    }

    @Override
    public String getName()
    {
        return String.valueOf(getAccount().getId());
    }

    public U getUser()
    {
        return user;
    }

}
