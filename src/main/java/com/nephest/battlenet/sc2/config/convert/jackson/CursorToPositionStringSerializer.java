// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.nephest.battlenet.sc2.model.navigation.Cursor;
import com.nephest.battlenet.sc2.model.navigation.CursorUtil;
import java.io.IOException;

public class CursorToPositionStringSerializer
extends StdSerializer<Cursor>
{

    public CursorToPositionStringSerializer()
    {
        super(Cursor.class);
    }

    @Override
    public void serialize
    (
        Cursor cursor,
        JsonGenerator jsonGenerator,
        SerializerProvider serializerProvider
    ) throws IOException
    {
        if(cursor == null)
        {
            jsonGenerator.writeNull();
            return;
        }

        ObjectMapper objectMapper = (ObjectMapper) serializerProvider.getGenerator().getCodec();
        jsonGenerator.writeString(CursorUtil.encodePosition(cursor.position(), objectMapper));
    }

}
