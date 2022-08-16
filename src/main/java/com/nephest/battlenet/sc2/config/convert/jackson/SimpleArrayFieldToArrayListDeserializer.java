// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class SimpleArrayFieldToArrayListDeserializer<C extends Collection<? extends E>, E>
extends StdDeserializer<C>
{

    private final Function<E[], C> collectionSupplier;
    private final Supplier<? extends E> elementSupplier;
    private final Map<String, ThrowingBiConsumer<JsonNode, E>> mappers;

    public SimpleArrayFieldToArrayListDeserializer
    (
        Class<?> vc,
        Function<E[], C> collectionSupplier,
        Supplier<? extends E> elementSupplier,
        Map<String, ThrowingBiConsumer<JsonNode, E>> mappers
    )
    {
        super(vc);
        this.collectionSupplier = collectionSupplier;
        this.elementSupplier = elementSupplier;
        this.mappers = mappers;
    }

    @Override
    @SuppressWarnings("unchecked")
    public C deserialize
    (
        JsonParser jsonParser,
        DeserializationContext deserializationContext
    ) throws IOException
    {
        JsonNode root = deserializationContext.readTree(jsonParser);
        Set<Map.Entry<String, ThrowingBiConsumer<JsonNode, E>>> entrySet = mappers.entrySet();
        String sizeTestField = entrySet.iterator().next().getKey();
        int size = root.get(sizeTestField).size();
        if(size == 0)
            return collectionSupplier.apply((E[]) Array.newInstance(handledType(), 0));

        E[] eArray = (E[]) Array.newInstance(handledType(), size);
        for(int i = 0; i < size; i++) eArray[i] = elementSupplier.get();
        for(Map.Entry<String, ThrowingBiConsumer<JsonNode, E>> entry : entrySet)
        {
            JsonNode valArray = root.get(entry.getKey());
            ThrowingBiConsumer<JsonNode, E> valueSetter = entry.getValue();
            for(int i = 0; i < size; i++)
                valueSetter.accept(valArray.get(i), eArray[i]);
        }
        return collectionSupplier.apply(eArray);
    }
}
