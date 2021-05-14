// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigInteger;

@Component
public class BigIntegerModule
extends SimpleModule
{

    public BigIntegerModule()
    {
        addSerializer(BigInteger.class, new StdScalarSerializer<>(BigInteger.class)
        {

            @Override
            public void serialize(BigInteger bigInteger, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException
            {
                jsonGenerator.writeString(bigInteger.toString());
            }

        });
    }

}
