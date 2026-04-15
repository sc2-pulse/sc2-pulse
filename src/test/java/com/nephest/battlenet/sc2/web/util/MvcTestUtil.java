// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.util;

import com.nephest.battlenet.sc2.util.hamcrest.FlattenedFeatureMatcher;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.hamcrest.Matcher;

public final class MvcTestUtil
{

    private MvcTestUtil()
    {
    }

    public static <I, O> Matcher<Iterable<? extends I>> flattened
    (
        Matcher<? super List<O>> subMatcher,
        Function<I, Stream<O>> flattener,
        Function<Stream<O>, Stream<O>> mapper
    )
    {
        return new FlattenedFeatureMatcher<>(subMatcher, flattener, mapper);
    }

    public static <I, O> Matcher<Iterable<? extends I>> flattened
    (
        Matcher<? super List<O>> subMatcher,
        Function<I, Stream<O>> flattener
    )
    {
        return new FlattenedFeatureMatcher<>(subMatcher, flattener);
    }

    public static Matcher<Iterable<? extends String>> flattened
    (
        String delimiter,
        Matcher<? super List<String>> subMatcher
    )
    {
        return flattened
        (
            subMatcher,
            i->Arrays.stream(i.split(delimiter)),
            s->s.map(String::trim)
        );
    }

    public static Matcher<Iterable<? extends String>> flattened
    (
        Matcher<? super List<String>> subMatcher
    )
    {
        return flattened(",",  subMatcher);
    }

}
