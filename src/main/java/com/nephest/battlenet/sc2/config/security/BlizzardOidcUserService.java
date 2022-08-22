// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.AccountRoleDAO;
import java.net.URL;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class BlizzardOidcUserService
implements Oauth2UserServiceRegistration<OidcUserRequest, OidcUser>
{

    private static final List<String> REGISTRATION_IDS = List.of
    (
        "sc2-lg-us",
        "sc2-lg-eu",
        "sc2-lg-kr",
        "sc2-lg-cn"
    );

    private final OidcUserService service = new OidcUserService();

    private final AccountDAO accountDAO;
    private final AccountRoleDAO accountRoleDAO;

    @Autowired
    public BlizzardOidcUserService
    (AccountDAO accountDAO, AccountRoleDAO accountRoleDAO)
    {
        this.accountDAO = accountDAO;
        this.accountRoleDAO = accountRoleDAO;
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
        return new BlizzardOidcUser(user, account, accountRoleDAO.getRoles(account.getId()));
    }

    @Override
    public Iterable<String> getRegistrationIds()
    {
        return REGISTRATION_IDS;
    }

}
