// Copyright (C) 2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;

@Component
public class BigDecimalModule
extends SimpleModule
{

    public BigDecimalModule()
    {
        addSerializer
        (
            BigDecimal.class,
            new StdScalarSerializer<>(BigDecimal.class)
            {

                @Override
                public void serialize
                (
                    BigDecimal bigDecimal,
                    JsonGenerator jsonGenerator,
                    SerializerProvider serializerProvider
                )
                throws IOException
                {
                    jsonGenerator.writeString(bigDecimal.toPlainString());
                }

            }
        );
    }

}
