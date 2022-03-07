// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
public class SimpleCollectionToFieldArraySerializerTest
{

    @Mock
    private JsonGenerator jsonGenerator;

    private SimpleCollectionToFieldArraySerializer<Object> serializer;

    @BeforeEach
    private void beforeEach()
    {
        Map<String, ThrowingBiConsumer<JsonGenerator, Object>> map = new LinkedHashMap<>();
        map.put("e1", (g, s)->g.writeString(s.toString()));
        map.put("e2", (g, s)->g.writeNumber(s.hashCode()));
        serializer = new SimpleCollectionToFieldArraySerializer<>(map);
    }

    @Test
    public void testSerialize()
    throws IOException
    {
        List<Object> objects = List.of(new Object(), new Object());
        serializer.serialize(objects, jsonGenerator, null);
        InOrder order = inOrder(jsonGenerator);

        order.verify(jsonGenerator).writeStartObject();

        order.verify(jsonGenerator).writeArrayFieldStart("e1");
        order.verify(jsonGenerator).writeString(objects.get(0).toString());
        order.verify(jsonGenerator).writeString(objects.get(1).toString());
        order.verify(jsonGenerator).writeEndArray();

        order.verify(jsonGenerator).writeArrayFieldStart("e2");
        order.verify(jsonGenerator).writeNumber(objects.get(0).hashCode());
        order.verify(jsonGenerator).writeNumber(objects.get(1).hashCode());
        order.verify(jsonGenerator).writeEndArray();

        order.verify(jsonGenerator).writeEndObject();

        order.verifyNoMoreInteractions();
    }

}
