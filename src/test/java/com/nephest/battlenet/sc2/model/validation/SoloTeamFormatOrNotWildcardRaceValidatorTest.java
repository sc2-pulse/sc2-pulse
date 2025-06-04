// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyId;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyIdEntry;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class SoloTeamFormatOrNotWildcardRaceValidatorTest
{

    private final SoloTeamFormatOrNotWildcardRaceValidator validator
        = new SoloTeamFormatOrNotWildcardRaceValidator();

    public static Stream<Arguments> testValidate()
    {
        TeamLegacyId soloWildcardId = TeamLegacyId
            .standard(List.of(new TeamLegacyIdEntry(1, 1L, true)));
        return Stream.of
        (
            Arguments.of
            (
                new TeamLegacyUid
                (
                    QueueType.LOTV_1V1,
                    TeamType.ARRANGED,
                    Region.EU,
                    soloWildcardId
                ),
                true
            ),
            Arguments.of
            (
                new TeamLegacyUid
                (
                    QueueType.LOTV_2V2,
                    TeamType.RANDOM,
                    Region.EU,
                    soloWildcardId
                ),
                true
            ),
            Arguments.of
            (
                new TeamLegacyUid
                (
                    QueueType.LOTV_2V2,
                    TeamType.ARRANGED,
                    Region.EU,
                    TeamLegacyId.standard(List.of(
                        new TeamLegacyIdEntry(1, 1L, Race.ZERG),
                        new TeamLegacyIdEntry(2, 2L, Race.TERRAN)
                    ))
                ),
                true
            ),
            Arguments.of
            (
                new TeamLegacyUid
                (
                    QueueType.LOTV_2V2,
                    TeamType.ARRANGED,
                    Region.EU,
                    TeamLegacyId.standard(List.of(
                        new TeamLegacyIdEntry(1, 1L, Race.ZERG),
                        new TeamLegacyIdEntry(2, 2L, true)
                    ))
                ),
                false
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testValidate(TeamLegacyUid uid, boolean expected)
    {
        assertEquals(expected, validator.isValid(uid, null));
    }

}
