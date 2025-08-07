// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.util;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record DiscriminatedTag(String tag, Long discriminator)
implements Comparable<DiscriminatedTag>
{

    public static final DiscriminatedTag EMPTY = new DiscriminatedTag(null, null);
    public static final Comparator<DiscriminatedTag> COMPARATOR
        = Comparator.comparing(DiscriminatedTag::tag, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(DiscriminatedTag::discriminator, Comparator.nullsLast(Comparator.naturalOrder()));
    public static final String DEFAULT_DELIMITER = "#";

    public static DiscriminatedTag parse
    (
        @NotBlank String str,
        @NotBlank String delimiter,
        Function<Long, Long> discriminatorTransformer
    )
    {
        String[] split = str.split(delimiter);
        return new DiscriminatedTag
        (
            split[0],
            split.length == 2
                ? discriminatorTransformer.apply(Long.parseLong(split[1]))
                : null
        );
    }

    public static DiscriminatedTag parse(@NotBlank String str)
    {
        return parse(str, DEFAULT_DELIMITER, Function.identity());
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof DiscriminatedTag that)) {return false;}
        return Objects.equals(tag, that.tag)
            && Objects.equals(discriminator, that.discriminator);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(tag, discriminator);
    }

    @Override
    public String toString()
    {
        return toString(DEFAULT_DELIMITER);
    }

    public String toString(String delimiter)
    {
        return Stream.of(tag, discriminator)
            .filter(Objects::nonNull)
            .map(String::valueOf)
            .collect(Collectors.joining(delimiter));
    }

    @Override
    public int compareTo(@NotNull DiscriminatedTag discriminatedTag)
    {
        return COMPARATOR.compare(this, discriminatedTag);
    }

}
