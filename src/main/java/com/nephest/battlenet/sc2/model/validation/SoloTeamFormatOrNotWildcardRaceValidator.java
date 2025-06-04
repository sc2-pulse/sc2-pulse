// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyIdEntry;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SoloTeamFormatOrNotWildcardRaceValidator
implements ConstraintValidator<SoloTeamFormatOrNotWildcardRace, TeamLegacyUid>
{

    public static boolean isSolo(TeamLegacyUid legacyUid)
    {
        return legacyUid.getQueueType().getTeamFormat().getMemberCount(legacyUid.getTeamType()) == 1;
    }

    public static boolean noWildcardRaces(TeamLegacyUid legacyUid)
    {
        return legacyUid.getId().getEntries().stream().noneMatch(TeamLegacyIdEntry::isWildcardRace);
    }

    @Override
    public boolean isValid
    (
        TeamLegacyUid legacyUid,
        ConstraintValidatorContext constraintValidatorContext
    )
    {
        return isSolo(legacyUid) || noWildcardRaces(legacyUid);
    }

}
