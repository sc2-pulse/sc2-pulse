// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class StringToOffsetDateTimeConverter
implements Converter<String, OffsetDateTime>
{
    @Override
    public OffsetDateTime convert(String s)
    {
        return s.contains("-")
            ? OffsetDateTime.parse(s)
            : OffsetDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(s)), ZoneId.systemDefault());
    }
}
