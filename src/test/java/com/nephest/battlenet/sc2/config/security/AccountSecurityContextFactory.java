// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import org.springframework.stereotype.Component;

@Component
public class AccountSecurityContextFactory
implements WithSecurityContextFactory<WithBlizzardMockUser>
{

    @Autowired
    private AccountDAO accountDAO;

    @Override
    public SecurityContext createSecurityContext(WithBlizzardMockUser withMockUser)
    {
        OidcUser oidcUser = mock(OidcUser.class);
        String subject = String.valueOf(withMockUser.subject());
        Map<String, Object> subMap = Map.of("sub", subject);
        when(oidcUser.getAttributes()).thenReturn(subMap);
        when(oidcUser.getClaims()).thenReturn(subMap);
        OAuth2User principal = new BlizzardOidcUser
        (
            oidcUser,
            new Account(withMockUser.id(), withMockUser.partition(), withMockUser.username()),
            "password",
            List.of()
        );
        Authentication auth = new OAuth2AuthenticationToken
        (
            principal,
            List.of(withMockUser.roles()),
            withMockUser.authorizedClientRegistrationId()
        );
        SecurityContext ctxt = SecurityContextHolder.createEmptyContext();
        ctxt.setAuthentication(auth);
        return ctxt;
    }

}
