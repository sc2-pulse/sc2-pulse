// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.convert.KeysetScrollPositionKeysToStringConverterTest;
import com.nephest.battlenet.sc2.model.navigation.NavigationDirection;
import com.nephest.battlenet.sc2.util.TestUtil;
import com.nephest.battlenet.sc2.web.service.StringService;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;

public class KeysetScrollPositionArgumentResolverTest
{

    private final KeysetScrollPositionArgumentResolver resolver
        = new KeysetScrollPositionArgumentResolver
        (
            new StringService(TestUtil.OBJECT_MAPPER)
        );

    Method method = KeysetScrollPositionArgumentResolverTest.class
        .getDeclaredMethod
        (
            "parameters",
            KeysetScrollPosition.class,
            String.class
        );

    public KeysetScrollPositionArgumentResolverTest()
    throws NoSuchMethodException {}

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
        return Arrays.stream(ScrollPosition.Direction.values())
            .flatMap(direction->KeysetScrollPositionKeysToStringConverterTest
                .positionEncodingArguments()
                .map(arg->Arguments.of(arg.get()[0], arg.get()[1], direction)));
    }

    private static NativeWebRequest createRequest
    (
        ScrollPosition.Direction direction,
        String token
    )
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if(token != null) request.setParameter
        (
            NavigationDirection.from(direction).getRelativePosition(),
            token
        );
        request.setParameter("otherParameter", "invalidCursorValue");
        return new ServletWebRequest(request);
    }

    @MethodSource
    @ParameterizedTest
    public void testResolve
    (
        Map<String, Object> position,
        String token,
        ScrollPosition.Direction direction
    )
    {
        NativeWebRequest nwr = createRequest(direction, token);
        KeysetScrollPosition result = (KeysetScrollPosition) resolver
            .resolveArgument(null, null, nwr, null);
        if(position == null || position.isEmpty())
        {
            assertNull(result);
        }
        else
        {
            assertEquals(ScrollPosition.of(position, direction), result);
        }
    }

    @Test
    public void shouldThrowOnInvalidToken()
    {
        String invalidToken = "invalidToken";
        NativeWebRequest nwr = createRequest
        (
            ScrollPosition.Direction.BACKWARD,
            invalidToken
        );
        assertThrows
        (
            IllegalArgumentException.class,
            ()->resolver.resolveArgument(null, null, nwr, null),
            "Invalid cursor position token: " + invalidToken
        );
    }

    private void parameters(KeysetScrollPosition cursor, String notCursor){}

}
