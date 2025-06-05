// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import com.fasterxml.jackson.databind.util.StdConverter;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyId;

public class TeamLegacyIdToStringConverter
extends StdConverter<TeamLegacyId, String>
{

    @Override
    public String convert(TeamLegacyId teamLegacyId)
    {
        return teamLegacyId.getId();
    }

}
