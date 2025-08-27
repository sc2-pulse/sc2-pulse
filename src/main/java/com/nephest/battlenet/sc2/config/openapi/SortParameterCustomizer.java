// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.openapi;

import com.nephest.battlenet.sc2.model.SortingOrder;
import com.nephest.battlenet.sc2.model.validation.AllowedField;
import com.nephest.battlenet.sc2.model.web.SortParameter;
import com.nephest.battlenet.sc2.util.SpringUtil;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.Arrays;
import org.springdoc.core.customizers.ParameterCustomizer;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestParam;

@Component
public class SortParameterCustomizer
implements ParameterCustomizer
{

    @Override
    @SuppressWarnings({"unchecked"})
    public Parameter customize(Parameter parameter, MethodParameter methodParameter)
    {
        if(!SpringUtil.getClass(methodParameter).isAssignableFrom(SortParameter.class))
            return parameter;

        AllowedField allowedField = methodParameter.getParameterAnnotation(AllowedField.class);
        if(allowedField == null) return parameter;

        Schema schema = OpenApiUtil.getSchema(parameter);
        schema.setType("string");
        schema.setEnum
        (
            Arrays.stream(allowedField.value())
                .flatMap
                (
                    field->Arrays.stream(SortingOrder.values())
                        .map(order->new SortParameter(field, order))
                )
                .map(SortParameter::toPrefixedString)
                .toList()
        );

        RequestParam requestParam = methodParameter.getParameterAnnotation(RequestParam.class);
        if(requestParam != null) schema.setDefault(requestParam.defaultValue());
        return parameter;
    }

}
