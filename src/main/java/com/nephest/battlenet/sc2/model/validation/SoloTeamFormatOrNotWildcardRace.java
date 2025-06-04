// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = SoloTeamFormatOrNotWildcardRaceValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SoloTeamFormatOrNotWildcardRace
{

    String message() default "Wildcard race is only allowed in solo modes";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

}
