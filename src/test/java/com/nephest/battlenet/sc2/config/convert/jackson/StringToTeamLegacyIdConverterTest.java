// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyId;
import org.junit.jupiter.api.Test;

public class StringToTeamLegacyIdConverterTest
{

    private final StringToTeamLegacyIdConverter converter = new StringToTeamLegacyIdConverter();

    @Test
    public void testConvert()
    {
        assertEquals
        (
            TeamLegacyId.trusted(TeamLegacyIdToStringConverterTest.ID),
            converter.convert(TeamLegacyIdToStringConverterTest.ID)
        );
    }

}
