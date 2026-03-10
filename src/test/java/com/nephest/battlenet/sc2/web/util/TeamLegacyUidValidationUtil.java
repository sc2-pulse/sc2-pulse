// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.util;

import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import com.nephest.battlenet.sc2.model.validation.EntryCountMatchesPlayerCountValidatorTest;
import com.nephest.battlenet.sc2.model.validation.SoloTeamFormatOrNotWildcardRaceValidatorTest;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public final class TeamLegacyUidValidationUtil
{

    private TeamLegacyUidValidationUtil(){}

    public static Stream<TeamLegacyUid> invalidTeamLegacyUids()
    {
        return Stream.concat
        (
            SoloTeamFormatOrNotWildcardRaceValidatorTest.testValidate()
                .filter(args->!((boolean) args.get()[1]))
                .map(args->args.get()[0]),
            EntryCountMatchesPlayerCountValidatorTest.testValidate()
                .filter(args->!((boolean) args.get()[1]))
                .map(args->args.get()[0])

        )
            .map(obj->(TeamLegacyUid) obj);
    }

    public static Stream<Arguments> invalidTeamLegacyUidValidationArgs()
    {
        return invalidTeamLegacyUids()
            .map(teamLegacyUid->Arguments.of(
                "Validation failure",
                new HashMap<>(Map.of("teamLegacyUid", teamLegacyUid))
            ));
    }


}
