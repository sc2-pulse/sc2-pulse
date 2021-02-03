// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.model.local.Account;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.*;

public class BlizzardOidcUser
implements OidcUser
{

    private final OidcUser user;
    private final Account account;
    private Set<GrantedAuthority> authorities;

    public BlizzardOidcUser(OidcUser user, Account account, GrantedAuthority... authorities)
    {
        this.user = user;
        this.account = account;
        if(authorities.length > 0)
        {
            this.authorities = new HashSet<>(user.getAuthorities());
            this.authorities.addAll(Arrays.asList(authorities));
        }
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
    public Collection<? extends GrantedAuthority> getAuthorities()
    {
        return authorities == null ? user.getAuthorities() : authorities;
    }

    @Override
    public String getName()
    {
        return user.getName();
    }

    public Account getAccount()
    {
        return account;
    }

}
