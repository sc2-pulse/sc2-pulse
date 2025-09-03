// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.model.navigation.Cursor;
import com.nephest.battlenet.sc2.model.navigation.CursorUtilTest;
import com.nephest.battlenet.sc2.model.navigation.NavigationDirection;
import com.nephest.battlenet.sc2.model.navigation.Position;
import com.nephest.battlenet.sc2.util.TestUtil;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;

public class CursorArgumentResolverTest
{

    private final CursorArgumentResolver resolver
        = new CursorArgumentResolver(TestUtil.OBJECT_MAPPER);

    Method method = CursorArgumentResolverTest.class
        .getDeclaredMethod("parameters", Cursor.class, String.class);

    public CursorArgumentResolverTest() throws NoSuchMethodException {}

    @Test
    public void shouldSupportCursor()
    {
        assertTrue(resolver.supportsParameter(new MethodParameter(method, 0)));
    }

    @Test
    public void shouldNotSupportNonCursor()
    {
        assertFalse(resolver.supportsParameter(new MethodParameter(method, 1)));
    }

    public static Stream<Arguments> testResolve()
    {
        return Arrays.stream(NavigationDirection.values())
            .flatMap(direction->CursorUtilTest.positionEncodingArguments()
                .map(arg->Arguments.of(arg.get()[0], arg.get()[1], direction)));
    }

    @MethodSource
    @ParameterizedTest
    public void testResolve(Position position, String token, NavigationDirection direction)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter(direction.getRelativePosition(), token);
        request.setParameter("otherParameter", "invalidCursorValue");
        NativeWebRequest nwr = new ServletWebRequest(request);
        Cursor result = (Cursor) resolver.resolveArgument(null, null, nwr, null);
        if(position == null)
        {
            assertNull(result);
        }
        else
        {
            assertEquals(new Cursor(position, direction), result);
        }
    }

    private void parameters(Cursor cursor, String notCursor){}

}
