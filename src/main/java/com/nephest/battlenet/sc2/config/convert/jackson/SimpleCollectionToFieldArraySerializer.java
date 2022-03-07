// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public class SimpleCollectionToFieldArraySerializer<E>
extends StdSerializer<Collection<? extends E>>
{

    private final Map<String, ThrowingBiConsumer<JsonGenerator, E>> mappers;

    public SimpleCollectionToFieldArraySerializer(Map<String, ThrowingBiConsumer<JsonGenerator, E>> mappers)
    {
        super((Class<Collection<? extends E>>) null);
        this.mappers = mappers;
    }

    protected SimpleCollectionToFieldArraySerializer
    (Class<Collection<? extends E>> t, Map<String, ThrowingBiConsumer<JsonGenerator, E>> mappers)
    {
        super(t);
        this.mappers = mappers;
    }

    @Override
    public void serialize
    (Collection<? extends E> collection, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
    throws IOException
    {
        jsonGenerator.writeStartObject();
        for(Map.Entry<String, ThrowingBiConsumer<JsonGenerator, E>> e : mappers.entrySet())
            writeArray(jsonGenerator, e.getKey(), collection, e.getValue());
        jsonGenerator.writeEndObject();
    }

    private static <E> void writeArray
    (
        JsonGenerator jsonGenerator,
        String name,
        Collection<? extends E> collection,
        ThrowingBiConsumer<JsonGenerator, E> consumer
    )
    throws IOException
    {
        jsonGenerator.writeArrayFieldStart(name);
        for(E e : collection) consumer.acceptThrows(jsonGenerator, e);
        jsonGenerator.writeEndArray();
    }

}
