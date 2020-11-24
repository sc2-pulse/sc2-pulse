// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.nephest.battlenet.sc2.model.SocialMedia;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;

public class SocialMediaMapDeserializer
extends StdDeserializer<Map<SocialMedia, String>>
{

    public SocialMediaMapDeserializer()
    {
        this(null);
    }

    protected SocialMediaMapDeserializer(Class<?> vc)
    {
        super(vc);
    }

    @Override
    public Map<SocialMedia, String> deserialize
    (
        JsonParser jsonParser, DeserializationContext deserializationContext
    )
    throws IOException
    {
        Map<SocialMedia, String> result = new EnumMap<>(SocialMedia.class);
        JsonNode node = jsonParser.getCodec().readTree(jsonParser);
        for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); )
        {
            Map.Entry<String, JsonNode> entry = it.next();
            String url = entry.getValue().asText();
            if(url == null || url.isEmpty()) continue;

            result.put(SocialMedia.fromRevealedName(entry.getKey()), url);
        }
        return result;
    }

}
