// Copyright (C) 2020-2024 Oleksandr Masniuk
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
        in = ParameterIn.QUERY, name = "legacyUid", description = "queueId-regionId-legacyId",
        array = @ArraySchema
        (
            maxItems = TeamGroupArgumentResolver.LEGACY_UIDS_MAX,
            schema = @Schema(type = "string", pattern = "[0-9]{3}-[0-9]-[0-9]+")
        )
    ),
    @Parameter
    (
        in = ParameterIn.QUERY, name = "fromSeason",
        schema = @Schema(type = "integer", format = "int32", minimum = "0")
    ),
    @Parameter
    (
        in = ParameterIn.QUERY, name = "toSeason",
        schema = @Schema(type = "integer", format = "int32", minimum = "0")
    )
})
public @interface TeamGroup
{

    boolean flatRequired() default true;

}
