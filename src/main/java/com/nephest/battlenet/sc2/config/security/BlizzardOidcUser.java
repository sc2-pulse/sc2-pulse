// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.model.local.Account;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.Map;

public class BlizzardOidcUser
extends AccountUser
implements OidcUser
{

    private final OidcUser user;

    public BlizzardOidcUser(OidcUser user, Account account, Collection<? extends SC2PulseAuthority> authorities)
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
    public Map<String, Object> getClaims()
    {
        return user.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo()
    {
        return user.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken()
    {
        return user.getIdToken();
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

}
