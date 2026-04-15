// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.util.hamcrest;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;

public class FlattenedFeatureMatcher<I, O>
extends FeatureMatcher<Iterable<? extends I>, List<O>>
{

    private final Function<I, Stream<O>> flattener;
    private final Function<Stream<O>, Stream<O>> mapper;

    public FlattenedFeatureMatcher
    (
        Matcher<? super List<O>> subMatcher,
        String featureName,
        Function<I, Stream<O>> flattener,
        Function<Stream<O>, Stream<O>> mapper
    )
    {
        super(subMatcher, "a flattened " + featureName + " list", featureName);
        this.flattener = flattener;
        this.mapper = mapper;
    }

    public FlattenedFeatureMatcher
    (
        Matcher<? super List<O>> subMatcher,
        Function<I, Stream<O>> flattener,
        Function<Stream<O>, Stream<O>> mapper
    )
    {
        this(subMatcher, "arg", flattener, mapper);
    }

    public FlattenedFeatureMatcher
    (
        Matcher<? super List<O>> subMatcher,
        Function<I, Stream<O>> flattener
    )
    {
        this(subMatcher, flattener, Function.identity());
    }

    @Override
    protected List<O> featureValueOf(Iterable<? extends I> actual)
    {
        return mapper.apply
        (
            StreamSupport.stream(actual.spliterator(), false)
                .flatMap(i->i == null ? null : flattener.apply(i))
        )
            .toList();
    }

}
