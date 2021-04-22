// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class StringToOffsetDateTimeConverter
implements Converter<String, OffsetDateTime>
{
    @Override
    public OffsetDateTime convert(String s)
    {
        return OffsetDateTime.parse(s);
    }
}
