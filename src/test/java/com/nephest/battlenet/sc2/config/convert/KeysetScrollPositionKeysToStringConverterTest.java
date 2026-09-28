// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.util.TestUtil;
import com.nephest.battlenet.sc2.web.service.StringService;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.ScrollPosition;

public class KeysetScrollPositionKeysToStringConverterTest
{

    private final KeysetScrollPositionKeysToStringConverter converter
        = new KeysetScrollPositionKeysToStringConverter
        (
            new StringService(TestUtil.OBJECT_MAPPER)
        );

    public static Stream<Arguments> positionEncodingArguments()
    {
        Map<String, Object> first = Map.of("one", "123");
        Map<String, Object> second = Map.of("two", 345);

        return Stream.of
        (
            Arguments.of(first, "eyJvbmUiOiIxMjMifQ"),
            Arguments.of(second, "eyJ0d28iOjM0NX0"),
            Arguments.of(Map.of(), null),
            Arguments.of(null, null)
        );
    }

    @MethodSource("positionEncodingArguments")
    @ParameterizedTest
    public void testConvert(Map<String, Object> position, String token)
    {
        assertEquals
        (
            token,
            position == null || position.isEmpty()
                ? null
                : converter.convert(ScrollPosition.backward(position))
        );
    }

}
