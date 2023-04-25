// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.AccountProperty;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.AccountPropertyDAO;
import com.nephest.battlenet.sc2.model.local.dao.AccountRoleDAO;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service("userDetailsService")
public class AccountUserDetailsService
implements UserDetailsService
{

    private final AccountDAO accountDAO;
    private final AccountPropertyDAO accountPropertyDAO;
    private final AccountRoleDAO accountRoleDAO;

    @Autowired
    public AccountUserDetailsService
    (
        AccountDAO accountDAO,
        AccountPropertyDAO accountPropertyDAO,
        AccountRoleDAO accountRoleDAO
    )
    {
        this.accountDAO = accountDAO;
        this.accountPropertyDAO = accountPropertyDAO;
        this.accountRoleDAO = accountRoleDAO;
    }

    @Override
    public UserDetails loadUserByUsername(String s)
    throws UsernameNotFoundException
    {
        List<Account> accs = accountDAO.findByIds(Long.valueOf(s));
        if(accs.isEmpty()) throw new UsernameNotFoundException("User not found");
        Account acc = accs.get(0);
        String password = accountPropertyDAO.find(acc.getId(), AccountProperty.PropertyType.PASSWORD)
            .map(AccountProperty::getValue)
            .orElseThrow(()->new DisabledException("Account " + s + " has no password"));
        List<SC2PulseAuthority> authorities = accountRoleDAO.getRoles(acc.getId());

        return new AccountUser(acc, password, authorities);
    }

}
