// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ClickHouseUtilTest
{

    static Stream<Arguments> quoteStringCases()
    {
        return Stream.of
        (
            Arguments.of("simple string", "hello", "'hello'"),
            Arguments.of("empty string", "", "''"),
            Arguments.of("single quote", "it's", "'it\\'s'"),
            Arguments.of("backslash", "C:\\Users", "'C:\\\\Users'"),
            Arguments.of("backslash then quote", "it\\'s", "'it\\\\\\'s'"),
            Arguments.of("multiple quotes", "a'b'c", "'a\\'b\\'c'"),
            Arguments.of("only special chars", "'\\", "'\\'\\\\'")
        );
    }

    static Stream<Arguments> toInClauseCases()
    {
        return Stream.of
        (
            Arguments.of("single value", List.of("foo"), "('foo')"),
            Arguments.of("multiple values", List.of("foo", "bar", "baz"), "('foo','bar','baz')"),
            Arguments.of("empty list", List.of(), "()"),
            Arguments.of(
                "values with quotes", List.of("it's", "C:\\Users"),
                "('it\\'s','C:\\\\Users')"
            ), Arguments.of("value with spaces", List.of("hello world"), "('hello world')"),
            Arguments.of("empty string value", List.of("a", "", "b"), "('a','','b')")
        );
    }

    @ParameterizedTest(name = "{0}: quoteString({1}) == {2}")
    @MethodSource("quoteStringCases")
    void quoteString(String description, String input, String expected)
    {
        assertEquals(expected, ClickHouseUtil.quote(input));
    }

    @ParameterizedTest(name = "{0}: toInClause({1}) == {2}")
    @MethodSource("toInClauseCases")
    void toInClause(String description, List<String> input, String expected)
    {
        assertEquals(expected, ClickHouseUtil.toInClause(input));
    }

}
