// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class CollectionVar<C extends Collection<T>, T>
extends Var<C>
{

    public static final String DELIMITER = ",";

    public CollectionVar
    (
        VarDAO varDAO,
        String key,
        Function<T, String> serializer,
        Function<String, T> deserializer,
        Supplier<C> emptyCollectionSupplier,
        Collector<? super T, ?, C> collector,
        boolean load
    )
    {
        super
        (
            varDAO,
            key,
            s->s == null || s.isEmpty() ? null : s.stream().map(serializer).collect(Collectors.joining(DELIMITER)),
            s->s == null || s.isEmpty()
                ? emptyCollectionSupplier.get()
                : Arrays.stream(s.split(DELIMITER)).map(deserializer).collect(collector),
            load
        );
        if(!load) setValue(emptyCollectionSupplier.get());
    }

    public CollectionVar
    (
        VarDAO varDAO,
        String key,
        Function<T, String> serializer,
        Function<String, T> deserializer,
        Supplier<C> emptyCollectionSupplier,
        Collector<? super T, ?, C> collector
    )
    {
        this(varDAO, key, serializer, deserializer, emptyCollectionSupplier, collector, true);
    }

}
