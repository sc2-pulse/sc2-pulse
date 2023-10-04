// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.config.security.AccountUser;
import com.nephest.battlenet.sc2.model.local.AccountProperty;
import com.nephest.battlenet.sc2.model.local.dao.AccountPropertyDAO;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AccountService
{

    private static final Logger LOG = LoggerFactory.getLogger(AccountService.class);

    private final AccountPropertyDAO accountPropertyDAO;
    private final PasswordEncoder passwordEncoder;
    private final StringKeyGenerator passwordGenerator;
    private final SessionRegistry sessionRegistry;

    @Autowired
    public AccountService
    (
        AccountPropertyDAO accountPropertyDAO,
        PasswordEncoder passwordEncoder,
        StringKeyGenerator passwordGenerator,
        SessionRegistry sessionRegistry
    )
    {
        this.accountPropertyDAO = accountPropertyDAO;
        this.passwordEncoder = passwordEncoder;
        this.passwordGenerator = passwordGenerator;
        this.sessionRegistry = sessionRegistry;
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
        accountPropertyDAO.merge(Set.of(property));
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

    public int invalidateSessions(@NonNull Long accountId)
    {
        generateNewPassword(accountId);
        int sessionsInvalidated = invalidateHttpSessions(accountId);
        if(sessionsInvalidated > 0) LOG.info
        (
            "Invalidated {} sessions of account {}",
            sessionsInvalidated,
            accountId
        );
        return sessionsInvalidated;
    }

    private int invalidateHttpSessions(@NonNull Long accountId)
    {
        AtomicInteger sessionsInvalidated = new AtomicInteger(0);
        sessionRegistry.getAllPrincipals().stream()
            .filter(principal->principal instanceof AccountUser)
            .map(principal->(AccountUser) principal)
            .filter(principal->Objects.equals(principal.getAccount().getId(), accountId))
            .flatMap(principal->sessionRegistry.getAllSessions(principal, false).stream())
            .peek(session->sessionsInvalidated.incrementAndGet())
            .forEach(SessionInformation::expireNow);
        return sessionsInvalidated.get();
    }

}
