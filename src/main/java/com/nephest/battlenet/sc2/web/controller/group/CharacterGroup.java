// Copyright (C) 2020-2023 Oleksandr Masniuk
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
        in = ParameterIn.QUERY, name = "characterId",
        array = @ArraySchema(maxItems = 500, schema = @Schema(type = "long"))
    ),
    @Parameter
    (
        in = ParameterIn.QUERY, name = "clanId",
        array = @ArraySchema(maxItems = 10, schema = @Schema(type = "integer"))
    )
})
public @interface CharacterGroup
{}
