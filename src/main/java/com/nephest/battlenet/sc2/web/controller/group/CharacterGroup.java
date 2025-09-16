// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller.group;

import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
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
        array = @ArraySchema
        (
            maxItems = CharacterGroupArgumentResolver.CHARACTERS_MAX,
            schema = @Schema(type = "integer", format = "int64"))
    ),
    @Parameter
    (
        in = ParameterIn.QUERY, name = "clanId",
        array = @ArraySchema
        (
            maxItems = CharacterGroupArgumentResolver.CLANS_MAX,
            schema = @Schema(type = "integer", format = "int32")
        )
    ),
    @Parameter
    (
        in = ParameterIn.QUERY, name = "proPlayerId",
        array = @ArraySchema
        (
            maxItems = CharacterGroupArgumentResolver.PRO_PLAYERS_MAX,
            schema = @Schema(type = "integer", format = "int64")
        )
    ),
    @Parameter
    (
        in = ParameterIn.QUERY, name = "accountId",
        array = @ArraySchema
        (
            maxItems = CharacterGroupArgumentResolver.ACCOUNTS_MAX,
            schema = @Schema(type = "integer", format = "int64")
        )
    ),
    @Parameter
    (
        in = ParameterIn.QUERY, name = "toonHandle",
        description = PlayerCharacterNaturalId.TOON_HANDLE_DESCRIPTION,
        array = @ArraySchema
        (
            maxItems = CharacterGroupArgumentResolver.TOON_HANDLES_MAX,
            schema = @Schema(ref = "ToonHandle")
        )
    )
})
public @interface CharacterGroup
{

    boolean flatRequired() default false;

}
