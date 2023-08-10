// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.util;

import com.nephest.battlenet.sc2.model.MultiAliasName;
import java.time.Duration;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MiscUtil
{

    private static final Logger LOG = LoggerFactory.getLogger(MiscUtil.class);

    private static List<Locale> COUNTRY_LOCALES;

    public static void awaitAndLogExceptions(List<Future<?>> tasks, boolean clear)
    {
        for(Future<?> task : tasks)
        {
            try
            {
                task.get();
            }
            catch (ExecutionException | InterruptedException e)
            {
                LOG.error(e.getMessage(), e);
            }
        }
        if(clear) tasks.clear();
    }

    public static void awaitAndThrowException(List<Future<?>> tasks, boolean clear, boolean delayException)
    {
        Exception cause = null;

        for(Future<?> f : tasks)
        {
            try
            {
                f.get();
            }
            catch (InterruptedException | ExecutionException e)
            {
                if(!delayException) throw new IllegalStateException(e);

                cause = e;
                LOG.error(e.getMessage(), e);
            }
        }

        if(cause != null) throw new IllegalStateException(cause);
        if(clear) tasks.clear();
    }

    public static Duration sinceHourStart(Temporal temporal)
    {
        return Duration.between
        (
            temporal
                .with(ChronoField.MINUTE_OF_HOUR, 0)
                .with(ChronoField.SECOND_OF_MINUTE, 0)
                .with(ChronoField.NANO_OF_SECOND, 0),
            temporal
        );
    }

    public static Duration untilNextHour(Temporal temporal)
    {
        return Duration.ofHours(1).minus(sinceHourStart(temporal));
    }

    public static double getHourProgress(Temporal temporal)
    {
        return (sinceHourStart(temporal).toSeconds() / 3600.0);
    }

    public static int stringLength(int n)
    {
        if(n == Integer.MIN_VALUE) return 10;
        n = Math.abs(n);

        return n<100000?n<100?n<10?1:2:n<1000?3:n<10000?4:5:
            n<10000000?n<1000000?6:7:n<100000000?8: n<1000000000?9:10;
    }

    public static <T extends Number & Comparable<T>> Range<T> parseRange
    (
        String str,
        Function<String, T> parser,
        Function<T, T> subtractor,
        boolean includingTo
    )
    {
        if(!str.contains("-") || str.length() < 3)
            throw new IllegalArgumentException("Invalid range format, "
                + "- not found or input is too short");

        str = str.replaceAll(" ", "");
        if(str.length() < 3) throw new IllegalArgumentException("Input is too short");

        int ix = str.indexOf("-");
        if(ix == 0) ix = str.indexOf("-", 1);
        if(ix == -1) throw new IllegalArgumentException("Invalid range");

        T from = parser.apply(str.substring(0, ix));
        T to = parser.apply(str.substring(ix + 1));
        if(!includingTo) to = subtractor.apply(to);
        if(from.compareTo(to) > 0)
            throw new IllegalArgumentException("Min value must be <= than max value");
        return Range.between(from, to);
    }

    public static <T extends MultiAliasName> List<T> findByAnyName
    (
        Map<T, Set<String>> nameMap,
        String name
    )
    {
        String nameLower = name.toLowerCase();
        return nameMap.entrySet().stream()
            .filter(e->e.getValue().contains(nameLower))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    public static <T extends Enum<T> & MultiAliasName>
    Map<T, Set<String>> generateAllNamesMap(Class<T> clazz)
    {
        return Collections.unmodifiableMap
        (
            Arrays.stream(clazz.getEnumConstants()).collect
            (
                Collectors.toMap
                (
                    Function.identity(),
                    t->t.getAllNames()
                        .map(String::toLowerCase)
                        .collect(Collectors.toUnmodifiableSet()),
                    (l, r)->{throw new IllegalStateException("Unexpected merge call");},
                    ()->new EnumMap<>(clazz)
                )
            )
        );
    }

    /**
     *
     * @param code ISO 3166-1 alpha-2 country code
     * @return country flag emoji
     */
    public static String countryCodeToEmoji(String code)
    {
        if(code == null || code.length() != 2)
            throw new IllegalArgumentException("Invalid code: " + code);

        code = code.toUpperCase();
        if (code.equals("UK")) code = "GB";
        StringBuilder sb = new StringBuilder();
        // offset between uppercase ASCII and regional indicator symbols
        for (int i = 0; i < code.length(); i++) sb.appendCodePoint(code.charAt(i) + 127397);
        return sb.toString();
    }

    public static List<Locale> getCountryLocales()
    {
        if(COUNTRY_LOCALES == null) COUNTRY_LOCALES = Arrays.stream(Locale.getISOCountries())
            .map(code->new Locale("", code))
            .collect(Collectors.toUnmodifiableList());
        return COUNTRY_LOCALES;
    }

}
