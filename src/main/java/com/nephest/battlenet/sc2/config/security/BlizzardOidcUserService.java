// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.net.URL;

public class BlizzardOidcUserService
implements OAuth2UserService<OidcUserRequest, OidcUser>
{

    private final OidcUserService service = new OidcUserService();

    private final AccountDAO accountDAO;

    public BlizzardOidcUserService(AccountDAO accountDAO)
    {
        this.accountDAO = accountDAO;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest r)
    throws OAuth2AuthenticationException
    {
        OidcUser user = service.loadUser(r);
        //merging because user can use the service without having any ladder stats(being absent in the db)
        Account account = accountDAO.merge(new Account(
            null,
            Partition.ofIssuer((URL) user.getAttribute("iss")),
            user.getAttribute("battle_tag"))
        );
        return new BlizzardOidcUser(user, account);
    }
}
