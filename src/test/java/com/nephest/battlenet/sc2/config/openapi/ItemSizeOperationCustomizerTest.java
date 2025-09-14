// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.openapi;


import static org.junit.jupiter.api.Assertions.assertEquals;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ItemSizeOperationCustomizerTest
{

    private final ItemsSizeOperationCustomizer customizer
        = new ItemsSizeOperationCustomizer();

    private static Parameter createParameter()
    {
        return new Parameter()
            .name("name")
            .description("desc")
            .in(SecurityScheme.In.QUERY.toString())
            .schema(new StringSchema());
    }

    public static Stream<Arguments> testCustomize()
    {
        return Stream.of
        (
            Arguments.of(10, 20, "*Min items:* 10, *Max items:* 20\n\ndesc"),
            Arguments.of(null, 20, "*Max items:* 20\n\ndesc"),
            Arguments.of(10, null, "*Min items:* 10\n\ndesc")
        );
    }

    @MethodSource
    @ParameterizedTest
    public void testCustomize(Integer min, Integer max, String expected)
    {
        Parameter minMaxParam = createParameter();
        minMaxParam.getSchema().setMinItems(min);
        minMaxParam.getSchema().setMaxItems(max);
        Operation originalOperation = new Operation().parameters(List.of(
            createParameter(),
            minMaxParam,
            createParameter()
        ));
        Operation operation = customizer.customize(originalOperation, null);
        assertEquals("desc", operation.getParameters().get(0).getDescription());
        assertEquals
        (
            expected,
            operation.getParameters().get(1).getDescription()
        );
        assertEquals("desc", operation.getParameters().get(2).getDescription());
    }

}
