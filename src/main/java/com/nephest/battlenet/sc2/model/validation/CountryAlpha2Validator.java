// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import java.util.Locale;
import java.util.Set;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class CountryAlpha2Validator
implements ConstraintValidator<CountryAlpha2, String>
{

    private final Set<String> codes = Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2);

    @Override
    public boolean isValid(String s, ConstraintValidatorContext constraintValidatorContext)
    {
        return s == null || codes.contains(s);
    }

}
