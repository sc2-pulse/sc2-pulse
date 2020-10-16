// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;

@Component
public class AccountSecurityContextFactory
implements WithSecurityContextFactory<WithBlizzardMockUser>
{

    @Autowired
    private AccountDAO accountDAO;

    @Override
    public SecurityContext createSecurityContext(WithBlizzardMockUser withMockUser)
    {

        List<GrantedAuthority> authorityList = new ArrayList<>();
        for(String role : withMockUser.roles()) authorityList.add(new SimpleGrantedAuthority("ROLE_" + role));

        OAuth2User principal =
            new BlizzardOidcUser(mock(OidcUser.class), accountDAO.merge(new Account(
                null, withMockUser.partition(), withMockUser.username())));
        Authentication auth = new OAuth2AuthenticationToken(principal, authorityList, withMockUser.username());
        SecurityContext ctxt = SecurityContextHolder.createEmptyContext();
        ctxt.setAuthentication(auth);
        return ctxt;
    }

}
