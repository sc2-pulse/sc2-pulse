// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.config.security.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PersistentLoginDAO
extends StandardDAO
{

    @Autowired
    public PersistentLoginDAO(@Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template)
    {
        super(template, "persistent_logins", "last_used", SecurityConfig.REMEMBER_ME_DURATION.toDays() + " DAYS");
    }

}
