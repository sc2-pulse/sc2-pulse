// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.local.dao.VarDAO;

import java.util.function.Function;

public class DoubleVar
extends Var<Double>
{

    public static final Function<Double, String> SERIALIZER = d->d == null ? null : String.valueOf(d);
    public static final Function<String, Double> DESERIALIZER = s->s == null || s.isEmpty() ? null : Double.parseDouble(s);

    public DoubleVar(VarDAO varDAO, String key, boolean load)
    {
        super(varDAO, key, SERIALIZER, DESERIALIZER, load);
    }

}
