// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.AccountRoleDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.web.service.AccountService;
import com.nephest.battlenet.sc2.web.service.BlizzardSC2API;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    private OidcUserService service = new OidcUserService();

    private final AccountDAO accountDAO;
    private final PlayerCharacterDAO playerCharacterDAO;
    private final AccountRoleDAO accountRoleDAO;
    private final AccountService accountService;
    private final BlizzardSC2API api;

    @Autowired
    public BlizzardOidcUserService
    (
        AccountDAO accountDAO,
        PlayerCharacterDAO playerCharacterDAO,
        AccountRoleDAO accountRoleDAO,
        AccountService accountService,
        BlizzardSC2API api
    )
    {
        this.accountDAO = accountDAO;
        this.playerCharacterDAO = playerCharacterDAO;
        this.accountRoleDAO = accountRoleDAO;
        this.accountService = accountService;
        this.api = api;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest r)
    throws OAuth2AuthenticationException
    {
        OidcUser user = service.loadUser(r);
        Account account = findOrMerge(user);
        return new BlizzardOidcUser
        (
            user,
            account,
            accountService.getOrGenerateNewPassword(account.getId()),
            accountRoleDAO.getRoles(account.getId())
        );
    }

    @Override
    public Iterable<String> getRegistrationIds()
    {
        return REGISTRATION_IDS;
    }

    /**
     * <ul>
     *     <li>Try to find existing account by partition and BattleTag.</li>
     *     <li>If not found, then load the characters from Blizzard profile and try to find the
     *     account by characters.</li>
     *     <li>If not found, then merge the account.</li>
     * </ul>
     * <p>This algorithm properly handles anonymous accounts. It reuses existing accounts(even
     * if they are anonymous) which prevents removal of old accounts and data that can be
     * associated with it.</p>
     *
     * @param user loaded oidc user
     * @return found or new account
     */
    private Account findOrMerge(OidcUser user)
    {
        Partition partition = Partition.ofIssuer((URL) user.getAttribute("iss"));
        String battleTag = user.getAttribute("battle_tag");
        return accountDAO.find(partition, battleTag)
            .or(()->findByBlizzardCharacters(user))
            .orElseGet(()->accountDAO.merge(new Account(null, partition, battleTag)));
    }

    private Optional<Account> findByBlizzardCharacters(OidcUser user)
    {
        return api.getPlayerCharacters(Region.EU, Long.valueOf(user.getSubject()))
            .toStream()
            .map(c->playerCharacterDAO.find(c.getRegion(), c.getRealm(), c.getBattlenetId()).orElse(null))
            .filter(Objects::nonNull)
            .findAny()
            .map(c->accountDAO.findByIds(Set.of(c.getAccountId())).get(0));
    }

    protected void setService(OidcUserService service)
    {
        this.service = service;
    }

}
