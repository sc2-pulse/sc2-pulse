// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import com.fasterxml.jackson.databind.util.StdConverter;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;

public class TeamLegacyUidToStringConverter
extends StdConverter<TeamLegacyUid, String>
{

    @Override
    public String convert(TeamLegacyUid legacyUid)
    {
        return legacyUid.toPulseString();
    }

}
