// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Represents ISO3166 Alpha2 country codes
 */

@Documented
@Constraint(validatedBy = CountryAlpha2Validator.class)
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CountryAlpha2
{

    String message() default "Must be a valid ISO3166 Alpha2 country code";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

}
