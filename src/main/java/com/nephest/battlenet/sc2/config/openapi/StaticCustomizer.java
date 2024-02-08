// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.openapi;

import com.nephest.battlenet.sc2.util.SpringUtil;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.Locale;
import java.util.Map;
import org.springdoc.core.customizers.ParameterCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;

/**
 * Rewrites types of object parameters based on pre-defined {@link #CLASS_TYPES}. Used for
 * converters that are not picked up by SpringDoc automatically.
 */
@Component
public class StaticCustomizer
implements ParameterCustomizer
{

    public static final Map<Class<?>, String> CLASS_TYPES = Map.of(Locale.class, "string");

    @Autowired
    public StaticCustomizer(){}

    @Override
    public Parameter customize(Parameter parameter, MethodParameter methodParameter)
    {
        String type = CLASS_TYPES.get(SpringUtil.getClass(methodParameter));
        if(type == null) return parameter;

        OpenApiUtil.getSchema(parameter).setType(type);
        return parameter;
    }

}
