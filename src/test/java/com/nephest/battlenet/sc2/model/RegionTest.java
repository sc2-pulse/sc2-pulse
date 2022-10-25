// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.util.MiscUtil;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class RegionTest
{

    public static Stream<Arguments> testFindByNameOrAdditionalName()
    {
        return Stream.of
        (
            Arguments.of("eU", List.of(Region.EU)),
            Arguments.of("euRope", List.of(Region.EU)),

            Arguments.of("uS", List.of(Region.US)),
            Arguments.of("United StaTes", List.of(Region.US)),
            Arguments.of("americA", List.of(Region.US)),
            Arguments.of("americas", List.of(Region.US)),
            Arguments.of("North America", List.of(Region.US)),

            Arguments.of("KR", List.of(Region.KR)),
            Arguments.of("korEa", List.of(Region.KR)),
            Arguments.of("South Korea", List.of(Region.KR)),
            Arguments.of("tw", List.of(Region.KR)),
            Arguments.of("taiWaN", List.of(Region.KR)),
            Arguments.of("ASIA", List.of(Region.KR)),

            Arguments.of("CN", List.of(Region.CN)),
            Arguments.of("ChiNa", List.of(Region.CN))
        );
    }

    @MethodSource
    @ParameterizedTest
    public void testFindByNameOrAdditionalName(String name, List<Region> expectedResult)
    {
        assertEquals(expectedResult, MiscUtil.findByAnyName(Region.ALL_NAMES_MAP, name));
    }

}
