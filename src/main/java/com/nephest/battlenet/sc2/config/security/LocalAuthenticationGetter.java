// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * A getter to mask static access for tests.
 */
@Service
public class LocalAuthenticationGetter
{

    public Authentication getAuthentication()
    {
        return SecurityContextHolder.getContext().getAuthentication();
    }

}
