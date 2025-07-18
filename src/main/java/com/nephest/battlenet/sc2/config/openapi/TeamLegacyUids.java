// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.openapi;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/*TODO
    This annotation should be replaced with some global springdoc config for TeamLegacyUid. Can't
    find a way to do it fast atm.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
@Parameter
(
    description = "queueId-teamTypeId-regionId-legacyId",
    array = @ArraySchema
    (
        schema = @Schema(type = "string", pattern = "^(201|202|203|204|206)-(0|1)-(1|2|3|5)-.+$")
    )
)
public @interface TeamLegacyUids
{}
