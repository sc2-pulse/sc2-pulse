// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.Set;

public abstract class AbstractAllowedFieldValidator<T>
implements ConstraintValidator<AllowedField, T>
{

    private Set<String> allowedFields;

    public abstract String getField(T value);

    @Override
    public void initialize(AllowedField constraintAnnotation)
    {
        this.allowedFields = Set.copyOf(Arrays.asList(constraintAnnotation.value()));
    }

    @Override
    public boolean isValid(T value, ConstraintValidatorContext context)
    {
        String field = getField(value);
        if(field == null || allowedFields.contains(field)) return true;

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate
        (
            String.format("Invalid field '%s'. Allowed fields are: %s",
                field, String.join(", ", allowedFields))
        ).addConstraintViolation();
        return false;
    }

}
