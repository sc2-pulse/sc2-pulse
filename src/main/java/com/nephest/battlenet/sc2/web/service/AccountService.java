// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.local.AccountProperty;
import com.nephest.battlenet.sc2.model.local.dao.AccountPropertyDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AccountService
{

    private final AccountPropertyDAO accountPropertyDAO;
    private final PasswordEncoder passwordEncoder;
    private final StringKeyGenerator passwordGenerator;

    @Autowired
    public AccountService
    (
        AccountPropertyDAO accountPropertyDAO,
        PasswordEncoder passwordEncoder,
        StringKeyGenerator passwordGenerator
    )
    {
        this.accountPropertyDAO = accountPropertyDAO;
        this.passwordEncoder = passwordEncoder;
        this.passwordGenerator = passwordGenerator;
    }

    public PasswordEncoder getPasswordEncoder()
    {
        return passwordEncoder;
    }

    /**
     * Encodes {@code password} using {@link #passwordEncoder} and sets account's password to
     * encoded value.
     * @param accountId account id
     * @param password raw password
     * @return encoded password
     */
    public String setPassword(Long accountId, String password)
    {
        AccountProperty property = new AccountProperty
        (
            accountId,
            AccountProperty.PropertyType.PASSWORD,
            passwordEncoder.encode(password)
        );
        accountPropertyDAO.merge(property);
        return property.getValue();
    }

    public String generateNewPassword(Long accountId)
    {
        return setPassword(accountId, passwordGenerator.generateKey());
    }

    public String getOrGenerateNewPassword(Long accountId)
    {
        return accountPropertyDAO.find(accountId, AccountProperty.PropertyType.PASSWORD)
            .map(AccountProperty::getValue)
            .orElseGet(()->generateNewPassword(accountId));
    }

}
