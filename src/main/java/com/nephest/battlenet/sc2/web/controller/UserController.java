// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
public class UserController
{

    @Autowired
    private AccountDAO accountDAO;

    @GetMapping("/mods")
    public List<Account> getMods()
    {
        return accountDAO.findByRole(SC2PulseAuthority.MODERATOR);
    }

    @GetMapping("/mods/tags")
    public List<String> getModTags()
    {
        return getMods().stream()
            .map(Account::getBattleTag)
            .collect(Collectors.toList());
    }

}
