// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class EmptyStringModule
extends SimpleModule
{

    public EmptyStringModule()
    {
        addDeserializer(String.class, new StdScalarDeserializer<String>(String.class)
        {
            @Override
            public String deserialize(JsonParser jsonParser, DeserializationContext ctx)
            throws IOException
            {
                String s = jsonParser.getValueAsString();
                return s.isEmpty() ? null : s;
            }
        });
    }

}
