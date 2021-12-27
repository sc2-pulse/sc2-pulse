// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.local.dao.VarDAO;

import java.util.function.Function;

public class LongVar
extends Var<Long>
{

    public static final Function<Long, String> SERIALIZER = l->l == null ? null : String.valueOf(l);
    public static final Function<String, Long> DESERIALIZER = s->s == null || s.isEmpty() ? null : Long.parseLong(s);

    public LongVar(VarDAO varDAO, String key, boolean load)
    {
        super(varDAO, key, SERIALIZER, DESERIALIZER, load);
    }

}
