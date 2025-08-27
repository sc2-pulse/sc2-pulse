// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import com.nephest.battlenet.sc2.model.web.SortParameter;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SortParameterAllowedFieldValidator
implements ConstraintValidator<AllowedField, SortParameter>
{

    private Set<String> allowedFields;

    @Override
    public void initialize(AllowedField constraintAnnotation)
    {
        this.allowedFields = new HashSet<>(Arrays.asList(constraintAnnotation.value()));
    }

    @Override
    public boolean isValid(SortParameter value, ConstraintValidatorContext context)
    {
        if(value == null || allowedFields.contains(value.field())) return true;

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate
        (
            String.format("Invalid sort field '%s'. Allowed fields are: %s",
            value.field(), String.join(", ", allowedFields))
        ).addConstraintViolation();
        return false;
    }

}

