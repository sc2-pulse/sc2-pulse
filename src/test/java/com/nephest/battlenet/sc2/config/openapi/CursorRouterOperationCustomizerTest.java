// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.model.navigation.NavigationDirection;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CursorRouterOperationCustomizerTest
{

    private final CursorRouterOperationCustomizer customizer
        = new CursorRouterOperationCustomizer();

    private final Parameter beforeParameter = new Parameter()
        .name(NavigationDirection.BACKWARD.getRelativePosition())
        .in(SecurityScheme.In.QUERY.toString())
        .description("Opaque cursor token taken from the navigation object of previous results")
        .required(false);
    private final Parameter afterParameter = new Parameter()
        .name(NavigationDirection.FORWARD.getRelativePosition())
        .in(SecurityScheme.In.QUERY.toString())
        .description("Opaque cursor token taken from the navigation object of previous results")
        .required(false);
    private final Parameter cursorParameter = new Parameter()
        .name("cursor")
        .in(SecurityScheme.In.QUERY.toString());
    private final Parameter otherParameter1 = new Parameter()
        .name("otherParam1")
        .in(SecurityScheme.In.QUERY.toString());
    private final Parameter otherParameter2 = new Parameter()
        .name("otherParam2")
        .in(SecurityScheme.In.QUERY.toString());

    private void testCustomize(List<Parameter> parameters, List<Parameter> expected)
    {
        Operation operation = new Operation();
        operation.setParameters(parameters);
        List<Parameter> customizedParameters = customizer.customize(operation, null)
            .getParameters();
        assertEquals(expected, customizedParameters);
    }

    @Test
    public void shouldSkipEmptyParameters()
    {
        testCustomize(List.of(), List.of());
    }

    @Test
    public void shouldSkipNonCursorParameters()
    {
        testCustomize(List.of(otherParameter1), List.of(otherParameter1));
    }

    @Test
    public void shouldReplaceCursorParameter()
    {
        testCustomize
        (
            new ArrayList<>(List.of(
                otherParameter1,
                cursorParameter,
                otherParameter2
            )),
            new ArrayList<>(List.of(
                otherParameter1,
                beforeParameter,
                afterParameter,
                otherParameter2
            ))
        );
    }

}
