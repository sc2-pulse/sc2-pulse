// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import com.nephest.battlenet.sc2.model.local.Account;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class NotFakeSc2NameValidator
implements ConstraintValidator<NotFakeSc2Name, String>
{

    @Override
    public boolean isValid(String s, ConstraintValidatorContext constraintValidatorContext)
    {
        return s == null || !Account.isFakeBattleTag(s);
    }

}
