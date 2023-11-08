// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.config.security.AccountUser;
import com.nephest.battlenet.sc2.config.security.BlizzardOidcUser;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardFullPlayerCharacter;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class PersonalService
{

    private final BlizzardSC2API api;
    private final PostgreSQLUtils postgreSQLUtils;


    @Autowired
    public PersonalService(BlizzardSC2API api, PostgreSQLUtils postgreSQLUtils)
    {
        this.api = api;
        this.postgreSQLUtils = postgreSQLUtils;
    }

    public Optional<BlizzardOidcUser> getOidcUser()
    {
        Object principal = getAuthentication()
            .map(Authentication::getPrincipal)
            .orElseThrow();
        if(!(principal instanceof BlizzardOidcUser)) return Optional.empty();

        return Optional.of((BlizzardOidcUser) principal);
    }

    public List<BlizzardFullPlayerCharacter> getCharacters()
    {
        return getOidcUser()
            .map(u->u.getAccount().getPartition() == Partition.GLOBAL
                ? api.getPlayerCharacters(Region.EU, Long.parseLong(u.getSubject()))
                    .onErrorComplete()
                    .collectList()
                    .block()
                : new ArrayList<BlizzardFullPlayerCharacter>())
            .orElse(List.of());
    }

    public void setDbTransactionUserId(Long id)
    {
        postgreSQLUtils.setTransactionUserId(String.valueOf(id));
    }

    public void setDbTransactionUserId()
    {
        getAccountId().ifPresent(this::setDbTransactionUserId);
    }

    public static Optional<AccountUser> getPrincipal(Authentication authentication)
    {
        return Optional.ofNullable(authentication)
            .map(auth->(AccountUser) auth.getPrincipal());
    }

    public static Optional<Long> getAccountId(Authentication authentication)
    {
        return getPrincipal(authentication)
            .map(AccountUser::getAccount)
            .map(Account::getId);
    }

    public static Optional<Authentication> getAuthentication()
    {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication());
    }

    public static Optional<AccountUser> getPrincipal()
    {
        return getAuthentication().flatMap(PersonalService::getPrincipal);
    }

    public static Optional<Long> getAccountId()
    {
        return getAuthentication().flatMap(PersonalService::getAccountId);
    }

}
