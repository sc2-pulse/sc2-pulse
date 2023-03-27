// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;

public class UnsignedLongSerializer
extends StdSerializer<Long>
{

    protected UnsignedLongSerializer(Class<Long> t)
    {
        super(t);
    }

    @Override
    public void serialize
    (
        Long aLong,
        JsonGenerator jsonGenerator,
        SerializerProvider serializerProvider
    ) throws IOException
    {
        jsonGenerator.writeString(Long.toUnsignedString(aLong));
    }

}
