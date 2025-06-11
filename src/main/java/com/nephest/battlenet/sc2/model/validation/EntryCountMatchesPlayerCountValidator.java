// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EntryCountMatchesPlayerCountValidator
implements ConstraintValidator<EntryCountMatchesPlayerCount, TeamLegacyUid>
{

    @Override
    public boolean isValid
    (
        TeamLegacyUid teamLegacyUid,
        ConstraintValidatorContext constraintValidatorContext
    )
    {
        return teamLegacyUid.getQueueType()
            .getTeamFormat()
            .getMemberCount(teamLegacyUid.getTeamType())
            == teamLegacyUid.getId().getEntries().size();
    }

}
