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

public class EntryCountMatchesPlayerCountValidatorTest
{

    private final EntryCountMatchesPlayerCountValidator validator
        = new EntryCountMatchesPlayerCountValidator();

    public static Stream<Arguments> testValidate()
    {
        return Stream.of
        (
            Arguments.of
            (
                new TeamLegacyUid
                (
                    QueueType.LOTV_2V2,
                    TeamType.ARRANGED,
                    Region.EU,
                    TeamLegacyId.standard(List.of(
                        new TeamLegacyIdEntry(1, 2L, Race.ZERG),
                        new TeamLegacyIdEntry(3, 4L, Race.TERRAN)
                    ))
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
                    TeamLegacyId.standard(List.of(
                        new TeamLegacyIdEntry(1, 2L, Race.ZERG),
                        new TeamLegacyIdEntry(3, 4L, Race.TERRAN)
                    ))
                ),
                false
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testValidate(TeamLegacyUid uid, boolean isValid)
    {
        assertEquals(isValid, validator.isValid(uid, null));
    }

}
