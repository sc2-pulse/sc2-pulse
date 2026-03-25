// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = UInt32EpochSecondsTemporalAccessorValidator.class)
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface UInt32EpochSeconds
{

    long MIN = 0;
    long MAX = 4294967295L;

    String message() default "Must be in 0-4294967295(1970-01-01T00:00:00Z-2106-02-07T06:28:15Z)"
        + " range";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

}
