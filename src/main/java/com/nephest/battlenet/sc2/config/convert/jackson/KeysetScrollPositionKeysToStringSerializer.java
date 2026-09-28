// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.nephest.battlenet.sc2.web.service.SpringBeanService;
import com.nephest.battlenet.sc2.web.service.StringService;
import java.io.IOException;
import org.springframework.data.domain.KeysetScrollPosition;

public class KeysetScrollPositionKeysToStringSerializer
extends StdSerializer<KeysetScrollPosition>
{

    public KeysetScrollPositionKeysToStringSerializer()
    {
        super(KeysetScrollPosition.class);
    }

    @Override
    public void serialize
    (
        KeysetScrollPosition cursor,
        JsonGenerator jsonGenerator,
        SerializerProvider serializerProvider
    ) throws IOException
    {
        if(cursor == null || cursor.isInitial())
        {
            jsonGenerator.writeNull();
            return;
        }

        StringService strings = SpringBeanService.getBean(StringService.class);
        jsonGenerator.writeString(strings.encode(cursor.getKeys()));
    }

}
