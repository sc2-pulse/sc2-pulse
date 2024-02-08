// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.openapi;

import com.nephest.battlenet.sc2.util.SpringUtil;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.ParameterCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Component;

/**
 * Rewrites enum parameters. Ignores Jackson annotations and uses {@code mvcConversionService}
 * instead. This is useful when there are multiple
 * {@link org.springframework.core.convert.ConversionService ConversionServices} or when Spring
 * MVC uses different conversion rules.
 */
@Component
public class EnumPropertyCustomizer
implements ParameterCustomizer
{

    private final ConversionService mvcConversionService;

    @Autowired
    public EnumPropertyCustomizer
    (
        @Qualifier("mvcConversionService") ConversionService mvcConversionService
    )
    {
        this.mvcConversionService = mvcConversionService;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Parameter customize(Parameter parameter, MethodParameter methodParameter)
    {
        Class<?> type = SpringUtil.getClass(methodParameter);
        if(!type.isEnum()) return parameter;

        List<String> enums = Arrays.stream(type.getEnumConstants())
            .map(e->mvcConversionService.convert(e, String.class))
            .collect(Collectors.toList());
        /*
            Can be any schema here, but it should be safe to inject string info here because
            no type-specific features should be used by original schemes. This saves original
            parameters while overriding the enum constants.
         */
        Schema schema = parameter.getSchema().getItems() != null
            ? parameter.getSchema().getItems()
            : parameter.getSchema();
        schema.setEnum(enums);
        if(parameter.getSchema().getType().equalsIgnoreCase("integer"))
        {
            parameter.getSchema().setType("string");
            parameter.getSchema().setFormat(null);
        }
        return parameter;
    }


}
