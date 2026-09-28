// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.openapi;

import com.nephest.battlenet.sc2.config.convert.StringToSortConverter;
import com.nephest.battlenet.sc2.model.validation.AllowedField;
import com.nephest.battlenet.sc2.util.SpringUtil;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.Arrays;
import org.springdoc.core.customizers.ParameterCustomizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestParam;

@Component
public class SortParameterCustomizer
implements ParameterCustomizer
{

    private final ConversionService mvcConversionService;

    public SortParameterCustomizer
    (
        @Qualifier("mvcConversionService")
        ConversionService mvcConversionService
    )
    {
        this.mvcConversionService = mvcConversionService;
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public Parameter customize(Parameter parameter, MethodParameter methodParameter)
    {
        if(!SpringUtil.getClass(methodParameter).isAssignableFrom(Sort.class))
            return parameter;

        AllowedField allowedField = methodParameter.getParameterAnnotation(AllowedField.class);
        if(allowedField == null) return parameter;

        Schema schema = OpenApiUtil.getSchema(parameter);
        schema.setType("string");
        if(!allowedField.allowTraversal())
        {
            schema.setEnum
            (
                Arrays.stream(allowedField.value())
                    .flatMap
                    (
                        field->Arrays.stream(Sort.Direction.values())
                            .map(order->Sort.by(order, field))
                    )
                    .map(sort->mvcConversionService.convert(sort, String.class))
                    .toList()
            );
        }
        else
        {
            schema.setEnum(null);
            schema.setDescription
            (
                "Comma-separated sort fields. Allowed fields: "
                    + String.join
                        (
                            StringToSortConverter.DELIMITER,
                            allowedField.value()
                        )
            );
        }


        RequestParam requestParam = methodParameter.getParameterAnnotation(RequestParam.class);
        if(requestParam != null) schema.setDefault(requestParam.defaultValue());
        return parameter;
    }

}
