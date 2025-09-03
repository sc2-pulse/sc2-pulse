// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.nephest.battlenet.sc2.model.CursorNavigation;
import com.nephest.battlenet.sc2.model.navigation.Cursor;
import com.nephest.battlenet.sc2.model.navigation.CursorUtil;
import com.nephest.battlenet.sc2.model.navigation.NavigationDirection;
import com.nephest.battlenet.sc2.model.navigation.Position;
import java.io.IOException;

public class CursorNavigationDeserializer
extends StdDeserializer<CursorNavigation>
{

    public CursorNavigationDeserializer()
    {
        super(CursorNavigation.class);
    }

    @Override
    public CursorNavigation deserialize
    (
        JsonParser jsonParser,
        DeserializationContext deserializationContext
    ) throws IOException
    {
        JsonNode node = jsonParser.readValueAsTree();
        if(node == null) return null;

        ObjectMapper objectMapper = (ObjectMapper) deserializationContext.getParser().getCodec();
        Position beforePosition = CursorUtil.decodePosition
        (
            node.get(NavigationDirection.BACKWARD.getRelativePosition()).textValue(),
            objectMapper
        );
        Position afterPosition = CursorUtil.decodePosition
        (
            node.get(NavigationDirection.FORWARD.getRelativePosition()).textValue(),
            objectMapper
        );
        return new CursorNavigation
        (
            beforePosition != null
                ? new Cursor(beforePosition, NavigationDirection.BACKWARD)
                : null,
            afterPosition != null
                ? new Cursor(afterPosition, NavigationDirection.FORWARD)
                : null
        );
    }

}
