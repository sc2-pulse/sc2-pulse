// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

public class StdCleanStringDeserializer
extends StdDeserializer<String>
{

    public static final String EMOJI_REGEX = "[^\\p{L}\\p{M}\\p{N}\\p{P}\\p{Z}\\p{Cf}\\p{Cs}\\s]";

    public StdCleanStringDeserializer()
    {
        this(null);
    }

    protected StdCleanStringDeserializer(Class<?> vc)
    {
        super(vc);
    }

    @Override
    public String deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
    throws IOException
    {
        return jsonParser.getValueAsString().replaceAll(EMOJI_REGEX, "");
    }

}
