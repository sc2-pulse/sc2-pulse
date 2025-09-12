// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.openapi;

import com.nephest.battlenet.sc2.model.validation.AllowedField;
import com.nephest.battlenet.sc2.util.SpringUtil;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.Arrays;
import org.springdoc.core.customizers.ParameterCustomizer;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;

@Component
public class StringAllowedFieldParameterCustomizer
implements ParameterCustomizer
{

    @Override
    public Parameter customize(Parameter parameterModel, MethodParameter methodParameter)
    {
        if(SpringUtil.getClass(methodParameter) != String.class) return parameterModel;

        AllowedField allowedField = methodParameter.getParameterAnnotation(AllowedField.class);
        if(allowedField == null) return parameterModel;

        OpenApiUtil.getSchema(parameterModel).setEnum(Arrays.asList(allowedField.value()));
        return parameterModel;
    }

}
