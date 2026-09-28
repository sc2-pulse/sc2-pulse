// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.nephest.battlenet.sc2.extension.ExecutionPhase;
import com.nephest.battlenet.sc2.extension.WithMockedStatic;
import com.nephest.battlenet.sc2.util.TestUtil;
import com.nephest.battlenet.sc2.web.service.SpringBeanService;
import com.nephest.battlenet.sc2.web.service.StringService;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;

@WithMockedStatic(value = SpringBeanService.class, phase = ExecutionPhase.CLASS)
@TestInstance(Lifecycle.PER_CLASS)
public class KeysetScrollPositionKeysToStringSerializerSimpleIntegrationTest
{

    private MockedStatic<SpringBeanService> springBeanServiceMock;

    @BeforeAll
    public void beforeAll()
    {
        StringService stringService = new StringService(TestUtil.OBJECT_MAPPER);
        springBeanServiceMock
            .when(()->SpringBeanService.getBean(StringService.class))
            .thenReturn(stringService);
    }

    @MethodSource
    (
        "com.nephest.battlenet.sc2.config.convert"
            + ".KeysetScrollPositionKeysToStringConverterTest"
            + "#positionEncodingArguments"
    )
    @ParameterizedTest
    public void testConvert(Map<String, Object> position, String token)
    throws JsonProcessingException
    {
        TestDto dto = new TestDto
        (
            position == null
                ? null
                : ScrollPosition.forward(position)
        );
        assertEquals
        (
            "{\"cursor\":" + (token == null ? "null" : "\"" + token + "\"") + "}",
            TestUtil.OBJECT_MAPPER.writeValueAsString(dto)
        );
    }

    private record TestDto
    (
        @JsonSerialize(using = KeysetScrollPositionKeysToStringSerializer.class)
        KeysetScrollPosition cursor
    )
    {}

}
