// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.openapi;

import com.nephest.battlenet.sc2.model.navigation.NavigationDirection;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.stream.IntStream;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

@Component
public class CursorRouterOperationCustomizer
implements OperationCustomizer
{

    public static final String CURSOR_PARAMETER_NAME = "cursor";

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod)
    {
        if(operation.getParameters().isEmpty()) return operation;

        int parameterIx = IntStream.range(0, operation.getParameters().size())
            .filter(ix->operation.getParameters().get(ix).getName().equals(CURSOR_PARAMETER_NAME))
            .findAny()
            .orElse(-1);
        if(parameterIx == -1) return operation;

        operation.getParameters().remove(parameterIx);
        for(NavigationDirection direction : NavigationDirection.values())
            operation.getParameters().add(parameterIx, new Parameter()
                .name(direction.getRelativePosition())
                .in(SecurityScheme.In.QUERY.toString())
                .description("Opaque cursor token taken from the navigation object of previous results")
                .required(false));
        return operation;
    }

}
