// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public class SC2PulseAuthoritySubsetValidator
implements ConstraintValidator<SC2PulseAuthoritySubset, SC2PulseAuthority>
{

    private final Set<SC2PulseAuthority> anyOf = EnumSet.noneOf(SC2PulseAuthority.class);

    @Override
    public void initialize(SC2PulseAuthoritySubset constraint)
    {
        anyOf.addAll(Arrays.asList(constraint.anyOf()));
    }

    @Override
    public boolean isValid(SC2PulseAuthority value, ConstraintValidatorContext context)
    {
        return anyOf.contains(value);
    }

}

