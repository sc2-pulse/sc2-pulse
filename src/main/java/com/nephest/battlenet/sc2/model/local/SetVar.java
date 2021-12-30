// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.local.dao.VarDAO;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SetVar<T>
extends Var<Set<T>>
{

    public static final String DELIMITER = ",";

    public SetVar(VarDAO varDAO, String key, Function<T, String> serializer, Function<String, T> deserializer, boolean load)
    {
        super
        (
            varDAO,
            key,
            s->s == null || s.isEmpty() ? null : s.stream().map(serializer).collect(Collectors.joining(DELIMITER)),
            s->s == null || s.isEmpty()
                ? new HashSet<>()
                : Arrays.stream(s.split(DELIMITER)).map(deserializer).collect(Collectors.toSet()),
            load
        );
        if(!load) setValue(new HashSet<>());
    }

    public SetVar(VarDAO varDAO, String key, Function<T, String> serializer, Function<String, T> deserializer)
    {
        this(varDAO, key, serializer, deserializer, true);
    }

}
