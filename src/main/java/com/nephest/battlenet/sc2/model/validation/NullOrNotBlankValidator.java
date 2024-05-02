// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NullOrNotBlankValidator
implements ConstraintValidator<NullOrNotBlank, String>
{

    @Override
    public boolean isValid(String s, ConstraintValidatorContext constraintValidatorContext)
    {
        return s == null || !s.isBlank();
    }

}
