// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.model.local.Account;
import java.util.Collection;
import java.util.Map;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public class BlizzardOidcUser
extends AccountOauth2User<OidcUser>
implements OidcUser
{

    public BlizzardOidcUser
    (
        OidcUser user,
        Account account,
        String password,
        Collection<? extends SC2PulseAuthority> authorities
    )
    {
        super(user, account, password, authorities);
    }

    @Override
    public Map<String, Object> getClaims()
    {
        return getUser().getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo()
    {
        return  getUser().getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken()
    {
        return  getUser().getIdToken();
    }

}
