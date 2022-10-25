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

public class LeagueTypeTest
{

    public static Stream<Arguments> testFindByNameOrAdditionalName()
    {
        return Stream.of
        (
            Arguments.of("bronzE", List.of(BaseLeague.LeagueType.BRONZE)),

            Arguments.of("silveR", List.of(BaseLeague.LeagueType.SILVER)),

            Arguments.of("GOLD", List.of(BaseLeague.LeagueType.GOLD)),

            Arguments.of("pLatinum", List.of(BaseLeague.LeagueType.PLATINUM)),
            Arguments.of("plat", List.of(BaseLeague.LeagueType.PLATINUM)),

            Arguments.of
            (
                "metal",
                List.of
                (
                    BaseLeague.LeagueType.BRONZE,
                    BaseLeague.LeagueType.SILVER,
                    BaseLeague.LeagueType.GOLD,
                    BaseLeague.LeagueType.PLATINUM
                )
            ),

            Arguments.of("diamonD", List.of(BaseLeague.LeagueType.DIAMOND)),

            Arguments.of("masteR", List.of(BaseLeague.LeagueType.MASTER)),
            Arguments.of("masterS", List.of(BaseLeague.LeagueType.MASTER)),

            Arguments.of("GM", List.of(BaseLeague.LeagueType.GRANDMASTER)),
            Arguments.of("GranDMasteR", List.of(BaseLeague.LeagueType.GRANDMASTER)),
            Arguments.of("grand Master", List.of(BaseLeague.LeagueType.GRANDMASTER)),
            Arguments.of("grandmasters", List.of(BaseLeague.LeagueType.GRANDMASTER))
        );
    }

    @MethodSource
    @ParameterizedTest
    public void testFindByNameOrAdditionalName(String name, List<BaseLeague.LeagueType> expectedResult)
    {
        assertEquals(expectedResult, MiscUtil.findByAnyName(BaseLeague.LeagueType.ALL_NAMES_MAP, name));
    }

}
