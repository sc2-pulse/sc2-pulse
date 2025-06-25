// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class UpperSnakeCaseStrategyTest
{

    private final UpperSnakeCaseStrategy strategy = new UpperSnakeCaseStrategy();

    @CsvSource
    ({
        "someRandomCamelCase, SOME_RANDOM_CAMEL_CASE",
        "some_random_snake_case, SOME_RANDOM_SNAKE_CASE",
        "kebab-case-unchanged, KEBAB-CASE-UNCHANGED",
        "qwerty, QWERTY",
        ","
    })
    @ParameterizedTest
    public void testTranslate(String in, String out)
    {
        assertEquals(out, strategy.translate(in));
    }

}
