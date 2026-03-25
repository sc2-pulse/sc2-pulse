// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;

public class UInt32EpochSecondsTemporalAccessorValidator
implements ConstraintValidator<UInt32EpochSeconds, TemporalAccessor>
{

    public static final ChronoField CHRONO_FIELD = ChronoField.INSTANT_SECONDS;

    @Override
    public boolean isValid
    (
        TemporalAccessor temporalAccessor,
        ConstraintValidatorContext constraintValidatorContext
    )
    {
        if(temporalAccessor == null) return true;
        if(!temporalAccessor.isSupported(CHRONO_FIELD)) return false;

        long epochSeconds = temporalAccessor.getLong(CHRONO_FIELD);
        return UInt32EpochSeconds.MIN <= epochSeconds && epochSeconds <= UInt32EpochSeconds.MAX;
    }

}
