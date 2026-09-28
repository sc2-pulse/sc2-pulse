// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import com.nephest.battlenet.sc2.util.MiscUtil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public abstract class AbstractAllowedFieldValidator<T>
implements ConstraintValidator<AllowedField, T>
{

    private List<String> orderedAllowedFields;
    private Set<String> allowedFields;
    private boolean allowTraversal;
    private boolean allowDuplicates;

    public abstract List<String> getFields(T value);
    public abstract boolean isTraversable(T value);

    @Override
    public void initialize(AllowedField constraintAnnotation)
    {
        this.orderedAllowedFields = Arrays.asList(constraintAnnotation.value());
        this.allowedFields = Set.copyOf(orderedAllowedFields);
        this.allowTraversal = constraintAnnotation.allowTraversal();
        this.allowDuplicates = constraintAnnotation.allowDuplicates();
    }

    @Override
    public boolean isValid(T value, ConstraintValidatorContext context)
    {
        boolean valid = true;
        if(!allowTraversal && isTraversable(value))
        {
            context
                .buildConstraintViolationWithTemplate("Field traversal is forbidden")
                .addConstraintViolation();
            valid = false;
        }

        List<String> fields = getFields(value);
        if(valid == true && (fields == null || fields.isEmpty())) return true;

        if(!allowDuplicates && MiscUtil.containsDuplicates(fields))
        {
            context.buildConstraintViolationWithTemplate
            (
                "Duplicate fields are forbidden"
            )
                .addConstraintViolation();
            valid = false;
        }

        if(!allowedFields.containsAll(fields))
        {
            context.buildConstraintViolationWithTemplate("Invalid fields")
                .addConstraintViolation();
            valid = false;
        }

        if(valid == false)
        {
            context.disableDefaultConstraintViolation();
            return false;
        }

        return true;
    }

}
