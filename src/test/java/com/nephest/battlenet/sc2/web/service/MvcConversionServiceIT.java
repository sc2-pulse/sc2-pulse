// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.ConversionService;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = AllTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class MvcConversionServiceIT
{

    @Autowired @Qualifier("mvcConversionService")
    private ConversionService conversionService;

    public static Stream<Arguments> testConvertToString()
    {
        Instant instant = Instant.ofEpochMilli(1731946867123L);
        return Stream.of
        (
            Arguments.of
            (
                OffsetDateTime.ofInstant(instant, ZoneOffset.UTC),
                "2024-11-18T16:21:07.123Z"
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testConvertToString(Object in, String out)
    {
        assertEquals(out, conversionService.convert(in, String.class));
    }

}
