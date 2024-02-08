// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.openapi;

import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;

public final class OpenApiUtil
{

    private OpenApiUtil(){}

    @SuppressWarnings({"rawtypes"})
    public static Schema getSchema(Parameter parameter)
    {
        /*
            Can be any schema here, but it should be safe to inject string info here because
            no type-specific features should be used by original schemes. This saves original
            parameters while overriding the enum constants.
         */
        return parameter.getSchema().getItems() != null
            ? parameter.getSchema().getItems()
            : parameter.getSchema();
    }

}
