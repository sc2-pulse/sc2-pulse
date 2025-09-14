// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.openapi;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

@Component
public class ItemsSizeOperationCustomizer
implements OperationCustomizer
{

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod)
    {
        operation.getParameters().forEach(ItemsSizeOperationCustomizer::customizeParameter);
        return operation;
    }

    private static void customizeParameter(Parameter parameter)
    {
        Schema<?> schema = parameter.getSchema();
        if(schema == null) return;

        Integer minItems = schema.getMinItems();
        Integer maxItems = schema.getMaxItems();

        if(minItems == null && maxItems == null) return;

        StringBuilder description = new StringBuilder();
        if(minItems != null) description.append("*Min items:* ").append(minItems);
        if(maxItems != null)
        {
            if(minItems != null) description.append(", ");
            description.append("*Max items:* ").append(maxItems);
        }
        if(parameter.getDescription() != null) description.append("\n\n")
            .append(parameter.getDescription());

        parameter.setDescription(description.toString());
    }
}
