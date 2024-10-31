// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.min;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.QueueType;
import java.sql.Timestamp;
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
public class MinimalConverterServiceIT
{

    @Autowired @Qualifier("minimalConversionService")
    private ConversionService conversionService;

    public static Stream<Arguments> testConvert()
    {
        return Stream.of
        (
            Arguments.of
            (
                new QueueType[]{QueueType.LOTV_1V1, QueueType.LOTV_2V2},
                new Integer[]{201, 202}
            ),
            Arguments.of
            (
                new Timestamp[]{new Timestamp(12000L), new Timestamp(1L)},
                new Long[]{12L, 0L}
            ),
            Arguments.of
            (
                new String[]{"1", "2"},
                new String[]{"1", "2"}
            )
        );
    }

    @MethodSource
    @ParameterizedTest
    public void testConvert(Object[] src, Object[] expectedResult)
    {
        assertArrayEquals(expectedResult, conversionService.convert(src, Object[].class));
    }

}
