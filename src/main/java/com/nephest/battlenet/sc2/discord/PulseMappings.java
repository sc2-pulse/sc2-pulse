// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.Range;

/**
 Immutable collection of mappings suitable for caching. Changes in the supplied mappings do not
 propagate to internal mappings.
 */
public class PulseMappings<T>
{

    private final PulseMapping<Region, T> regionMappings;
    private final PulseMapping<BaseLeague.LeagueType, T> leagueMappings;
    private final PulseMapping<Race, T> raceMappings;
    private final PulseMapping<Range<Integer>, T> ratingMappings;
    private final Set<T> values;

    public PulseMappings
    (
        Map<Region, List<T>> regionMappings,
        Map<BaseLeague.LeagueType, List<T>> leagueMappings,
        Map<Race, List<T>> raceMappings,
        Map<Range<Integer>, List<T>> ratingMappings,
        Function<T, String> toString,
        String delimiter
    )
    {
        this.regionMappings = new PulseMapping<>(regionMappings, toString, delimiter);
        this.leagueMappings = new PulseMapping<>(leagueMappings, toString, delimiter);
        this.raceMappings = new PulseMapping<>(raceMappings, toString, delimiter);
        this.ratingMappings = new PulseMapping<>(ratingMappings, toString, delimiter);
        values = valuesStream().collect(Collectors.toUnmodifiableSet());
    }

    public PulseMapping<Region, T> getRegionMappings()
    {
        return regionMappings;
    }

    public PulseMapping<BaseLeague.LeagueType, T> getLeagueMappings()
    {
        return leagueMappings;
    }

    public PulseMapping<Race, T> getRaceMappings()
    {
        return raceMappings;
    }

    public PulseMapping<Range<Integer>, T> getRatingMappings()
    {
        return ratingMappings;
    }

    public boolean isEmpty()
    {
        return getRegionMappings().getMappings().isEmpty()
            && getLeagueMappings().getMappings().isEmpty()
            && getRaceMappings().getMappings().isEmpty()
            && getRatingMappings().getMappings().isEmpty();
    }

    public Stream<T> valuesStream()
    {
        return Stream.of
        (
            getRegionMappings().getMappings().values().stream().flatMap(Collection::stream),
            getLeagueMappings().getMappings().values().stream().flatMap(Collection::stream),
            getRaceMappings().getMappings().values().stream().flatMap(Collection::stream),
            getRatingMappings().getMappings().values().stream().flatMap(Collection::stream)
        )
            .flatMap(Function.identity());
    }

    public Set<T> getValues()
    {
        return values;
    }

}
