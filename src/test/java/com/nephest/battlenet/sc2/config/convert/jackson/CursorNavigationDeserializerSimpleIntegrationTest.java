// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nephest.battlenet.sc2.config.convert.KeysetScrollPositionKeysToStringConverterTest;
import com.nephest.battlenet.sc2.extension.ExecutionPhase;
import com.nephest.battlenet.sc2.extension.WithMockedStatic;
import com.nephest.battlenet.sc2.model.CursorNavigation;
import com.nephest.battlenet.sc2.util.TestUtil;
import com.nephest.battlenet.sc2.web.service.SpringBeanService;
import com.nephest.battlenet.sc2.web.service.StringService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.springframework.data.domain.ScrollPosition;

@WithMockedStatic(value = SpringBeanService.class, phase = ExecutionPhase.CLASS)
@TestInstance(Lifecycle.PER_CLASS)
public class CursorNavigationDeserializerSimpleIntegrationTest
{

    private MockedStatic<SpringBeanService> springBeanServiceMock;

    @SuppressWarnings("unchecked")
    private static final List<Map<String, Object>> POSITIONS
        = KeysetScrollPositionKeysToStringConverterTest.positionEncodingArguments()
            .map(a->(Map<String, Object>) a.get()[0])
            .toList();
    private static final List<String> TOKENS
        = KeysetScrollPositionKeysToStringConverterTest
            .positionEncodingArguments()
            .map(a->(String) a.get()[1])
            .toList();

    @BeforeAll
    public void setUp()
    {
        StringService stringService = new StringService(TestUtil.OBJECT_MAPPER);
        springBeanServiceMock
            .when(()->SpringBeanService.getBean(StringService.class))
            .thenReturn(stringService);
    }

    public static Stream<Arguments> testDeserialize()
    {
        return Stream.of
        (
            Arguments.of
            (
                "{\"after\": \"" + TOKENS.get(1) + "\""
                    + ", \"before\": \"" + TOKENS.get(0) + "\"}",
                new CursorNavigation
                (
                    ScrollPosition.backward(POSITIONS.get(0)),
                    ScrollPosition.forward(POSITIONS.get(1))
                )
            ),
            Arguments.of
            (
                "{\"after\": \"" + TOKENS.get(1) + "\""
                    + ", \"before\": null}",
                new CursorNavigation
                (
                    null,
                    ScrollPosition.forward(POSITIONS.get(1))
                )
            ),
            Arguments.of
            (
                "{\"after\": null"
                    + ", \"before\": \"" + TOKENS.get(0) + "\"}",
                new CursorNavigation
                (
                    ScrollPosition.backward(POSITIONS.get(0)),
                    null
                )
            ),
            Arguments.of
            (
                "{\"after\": null, \"before\": null}",
                new CursorNavigation(null, null)
            ),
            Arguments.of("null", null)
        );
    }

    @MethodSource
    @ParameterizedTest
    public void testDeserialize(String json, CursorNavigation expected)
    throws IOException
    {
        String dtoJson = "{\"navigation\": " + json + "}";
        TestDto dto = TestUtil.OBJECT_MAPPER.readValue(dtoJson, TestDto.class);
        assertEquals(expected, dto.navigation());
    }

    private record TestDto(@JsonDeserialize(using = CursorNavigationDeserializer.class) CursorNavigation navigation) {}

}
