// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import java.time.Duration;
import java.util.function.Function;

public class DurationVar
extends Var<Duration>
{

    public static final Function<Duration, String> SERIALIZER = d->d == null ? null : d.toString();
    public static final Function<String, Duration> DESERIALIZER = s->s == null || s.isEmpty()
        ? null
        : Duration.parse(s);

    public DurationVar(VarDAO varDAO, String key, boolean load)
    {
        super(varDAO, key, SERIALIZER, DESERIALIZER, load);
    }

}
