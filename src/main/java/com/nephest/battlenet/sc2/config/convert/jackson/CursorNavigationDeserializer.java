// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.nephest.battlenet.sc2.model.CursorNavigation;
import com.nephest.battlenet.sc2.model.navigation.NavigationDirection;
import com.nephest.battlenet.sc2.web.service.SpringBeanService;
import com.nephest.battlenet.sc2.web.service.StringService;
import java.io.IOException;
import org.springframework.data.domain.ScrollPosition;

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

        StringService strings = SpringBeanService.getBean(StringService.class);
        String before = node
            .path(NavigationDirection.BACKWARD.getRelativePosition())
            .textValue();
        String after = node
            .path(NavigationDirection.FORWARD.getRelativePosition())
            .textValue();
        return new CursorNavigation
        (
            before != null
                ? ScrollPosition.backward(strings.decode(
                        before, new TypeReference<>(){}
                    ))
                : null,
            after != null
                ? ScrollPosition.forward(strings.decode(
                        after, new TypeReference<>(){}
                    ))
                : null
        );
    }

}
