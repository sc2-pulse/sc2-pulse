// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TeamLegacyUidTest
{

    private static final TeamLegacyUid LEGACY_UID = new TeamLegacyUid
    (
        QueueType.LOTV_1V1,
        TeamType.ARRANGED,
        Region.US,
        "1234"
    );
    private static final String LEGACY_UID_STRING = "201-0-1-1234";

    @Test
    public void testToPulseString()
    {
        assertEquals(LEGACY_UID_STRING, LEGACY_UID.toPulseString());
    }

    @Test
    public void testParse()
    {
        assertEquals(LEGACY_UID, TeamLegacyUid.parse(LEGACY_UID_STRING));
    }

    public static Stream<Arguments> testExpandRaceWildcards()
    {
        TeamLegacyUid notWildcardId = new TeamLegacyUid
        (
            QueueType.LOTV_2V2,
            TeamType.RANDOM,
            Region.EU,
            TeamLegacyId.standard(List.of(new TeamLegacyIdEntry(1, 2L, false)))
        );
        return Stream.of
        (
            Arguments.of
            (
                new TeamLegacyUid
                (
                    QueueType.LOTV_2V2,
                    TeamType.RANDOM,
                    Region.EU,
                    TeamLegacyId.standard(List.of(new TeamLegacyIdEntry(1, 2L, true)))
                ),
                Arrays.stream(Race.values())
                    .map(race->new TeamLegacyUid(
                        QueueType.LOTV_2V2,
                        TeamType.RANDOM,
                        Region.EU,
                        TeamLegacyId.standard(List.of(new TeamLegacyIdEntry(1, 2L, race)))
                    ))
                    .toList()
            ),
            Arguments.of(notWildcardId, List.of(notWildcardId))
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testExpandRaceWildcards(TeamLegacyUid uid, List<TeamLegacyUid> expanded)
    {
        Assertions.assertThat(TeamLegacyUid.expandWildcards(uid).toList())
            .usingRecursiveComparison()
            .isEqualTo(expanded);
    }

}
