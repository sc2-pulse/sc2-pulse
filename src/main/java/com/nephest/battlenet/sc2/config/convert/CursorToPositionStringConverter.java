// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.navigation.Cursor;
import com.nephest.battlenet.sc2.model.navigation.CursorUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class CursorToPositionStringConverter
implements Converter<Cursor, String>
{

    private final ObjectMapper objectMapper;

    @Autowired
    public CursorToPositionStringConverter(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    @Override
    public String convert(Cursor source)
    {
        return CursorUtil.encodePosition(source.position(), objectMapper);
    }

}
