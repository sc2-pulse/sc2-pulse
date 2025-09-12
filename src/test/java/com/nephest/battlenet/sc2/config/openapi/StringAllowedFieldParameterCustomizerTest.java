// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.openapi;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nephest.battlenet.sc2.model.validation.AllowedField;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;

public class StringAllowedFieldParameterCustomizerTest
{

    private static final MethodParameter STRING_METHOD_PARAMETER;
    private static final MethodParameter INTEGER_METHOD_PARAMETER;
    private static final MethodParameter NOT_FIELD_METHOD_PARAMETER;

    private final StringAllowedFieldParameterCustomizer customizer
        = new StringAllowedFieldParameterCustomizer();

    static
    {
        try
        {
            STRING_METHOD_PARAMETER = new MethodParameter
            (
                StringAllowedFieldParameterCustomizerTest.class
                    .getDeclaredMethod("method", String.class, Integer.class, String.class),
                0
            );
            INTEGER_METHOD_PARAMETER = new MethodParameter
            (
                StringAllowedFieldParameterCustomizerTest.class
                    .getDeclaredMethod("method", String.class, Integer.class, String.class),
                1
            );
            NOT_FIELD_METHOD_PARAMETER = new MethodParameter
            (
                StringAllowedFieldParameterCustomizerTest.class
                    .getDeclaredMethod("method", String.class, Integer.class, String.class),
                2
            );
        }
        catch (NoSuchMethodException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static Parameter createParameter()
    {
        return new Parameter()
            .name("name")
            .in(SecurityScheme.In.QUERY.toString())
            .schema(new StringSchema());
    }

    @Test
    public void testCustomize()
    {
        Parameter parameter = createParameter();
        customizer.customize(parameter, STRING_METHOD_PARAMETER);
        assertEquals(List.of("field1", "field2"), parameter.getSchema().getEnum());
    }

    @Test
    public void shouldSkipNotStringMethodParameter()
    {
        Parameter parameter = createParameter();
        customizer.customize(parameter, INTEGER_METHOD_PARAMETER);
        assertNull(parameter.getSchema().getEnum());
    }

    @Test
    public void shouldSkipNotAnnotatedMethodParameter()
    {
        Parameter parameter = createParameter();
        customizer.customize(parameter, NOT_FIELD_METHOD_PARAMETER);
        assertNull(parameter.getSchema().getEnum());
    }

    private static void method
    (
        @AllowedField({"field1", "field2"}) String parameter1,
        @AllowedField({"field1", "field2"}) Integer parameter2,
        String parameter3
    )
    {}

}
