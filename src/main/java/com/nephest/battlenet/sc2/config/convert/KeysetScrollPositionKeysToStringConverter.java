// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import com.nephest.battlenet.sc2.web.service.StringService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.stereotype.Component;

@Component
public class KeysetScrollPositionKeysToStringConverter
implements Converter<KeysetScrollPosition, String>
{

    private final StringService stringService;

    public KeysetScrollPositionKeysToStringConverter(StringService stringService)
    {
        this.stringService = stringService;
    }

    @Override
    public String convert(KeysetScrollPosition source)
    {
        if(source.isInitial()) return null;

        return stringService.encode(source.getKeys());
    }

}
