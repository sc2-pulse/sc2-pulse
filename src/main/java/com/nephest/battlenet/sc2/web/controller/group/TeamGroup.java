// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller.group;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.METHOD})
@Parameter(hidden = true)
@Parameters
({
    @Parameter
    (
        in = ParameterIn.QUERY, name = "teamId",
        array = @ArraySchema
        (
            maxItems = TeamGroupArgumentResolver.TEAMS_MAX,
            schema = @Schema(type = "integer", format = "int64")
        )
    ),
    @Parameter
    (
        in = ParameterIn.QUERY,
        name = "legacyUid",
        description = "queueId-teamTypeId-regionId-legacyId",
        array = @ArraySchema
        (
            maxItems = TeamGroupArgumentResolver.LEGACY_UIDS_MAX,
            schema = @Schema
            (
                type = "string",
                pattern = "^(201|202|203|204|206)-(0|1)-(1|2|3|5)-.+$"
            )
        )
    ),
    @Parameter
    (
        in = ParameterIn.QUERY, name = "seasonMin",
        schema = @Schema(type = "integer", format = "int32", minimum = "0")
    ),
    @Parameter
    (
        in = ParameterIn.QUERY, name = "seasonMax",
        schema = @Schema(type = "integer", format = "int32", minimum = "0")
    )
})
public @interface TeamGroup
{

    boolean flatRequired() default true;

}
