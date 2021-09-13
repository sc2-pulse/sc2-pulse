// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.local.dao.VarDAO;

import java.time.Instant;

public class InstantVar
extends Var<Instant>
{

    public InstantVar(VarDAO varDAO, String key)
    {
        super
        (
            varDAO,
            key,
            i->i == null ? null : String.valueOf(i.toEpochMilli()),
            s->s == null || s.isEmpty() ? null : Instant.ofEpochMilli(Long.parseLong(s))
        );
    }

}
