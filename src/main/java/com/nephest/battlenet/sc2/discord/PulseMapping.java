// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
    Immutable collection of mappings suitable for caching. Changes in the supplied map do not
    propagate to internal mappings.
 */
public class PulseMapping<K, T>
{

    private final Map<K, List<T>> mappings;
    private final String string;

    public PulseMapping
    (
        Map<K, List<T>> mappings,
        Function<T, String> toString,
        String delimiter
    )
    {
        this.mappings = mappings.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e->List.copyOf(e.getValue())));
        this.string = mappings.values().stream()
            .flatMap(Collection::stream)
            .map(toString)
            .distinct()
            .collect(Collectors.joining(delimiter));
    }

    @Override
    public String toString()
    {
        return getString();
    }

    public Map<K, List<T>> getMappings()
    {
        return mappings;
    }

    public String getString()
    {
        return string;
    }

}
