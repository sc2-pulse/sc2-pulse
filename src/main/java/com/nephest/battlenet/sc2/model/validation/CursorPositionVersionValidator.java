// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import com.nephest.battlenet.sc2.model.navigation.Cursor;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CursorPositionVersionValidator
implements ConstraintValidator<Version, Cursor>
{

    private long version;

    @Override
    public void initialize(Version constraintAnnotation)
    {
        this.version = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(Cursor cursor, ConstraintValidatorContext constraintValidatorContext)
    {
        return cursor == null || cursor.position().version() == version;
    }

}
