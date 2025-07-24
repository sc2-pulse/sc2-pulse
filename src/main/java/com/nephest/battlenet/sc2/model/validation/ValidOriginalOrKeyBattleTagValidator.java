// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import com.nephest.battlenet.sc2.model.blizzard.BlizzardAccount;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardAccountKey;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidOriginalOrKeyBattleTagValidator
implements ConstraintValidator<ValidOriginalOrKeyBattleTag, BlizzardAccount>
{

    @Override
    public boolean isValid
    (
        BlizzardAccount blizzardAccount,
        ConstraintValidatorContext constraintValidatorContext
    )
    {
        return
        (
            blizzardAccount.getOriginalBattleTag() != null
            && blizzardAccount.isOriginalBattleTagValid()
        )
        ||
        (
            blizzardAccount.getKey() != null
            && blizzardAccount.getKey().getHref() != null
            && BlizzardAccountKey.HREF_BATTLE_TAG_PATTERN
                .matcher(blizzardAccount.getKey().getHref())
                .matches()
        );
    }

}
