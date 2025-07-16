// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.openapi;

import com.nephest.battlenet.sc2.util.SpringUtil;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import org.springdoc.core.customizers.ParameterCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;

/**
 * Customizes parameters based on pre-defined {@link #CLASS_CUSTOMIZERS}. Used for
 * converters that are not picked up by SpringDoc automatically.
 */
@Component
public class StaticCustomizer
implements ParameterCustomizer
{

    public static final Map<Class<?>, Consumer<Parameter>> CLASS_CUSTOMIZERS = Map.of
    (
        Locale.class, parameter->OpenApiUtil.getSchema(parameter).setType("string")
    );

    @Autowired
    public StaticCustomizer(){}

    @Override
    public Parameter customize(Parameter parameter, MethodParameter methodParameter)
    {
        Consumer<Parameter> customizer = CLASS_CUSTOMIZERS
            .get(SpringUtil.getClass(methodParameter));
        if(customizer == null) return parameter;

        customizer.accept(parameter);
        return parameter;
    }

}
