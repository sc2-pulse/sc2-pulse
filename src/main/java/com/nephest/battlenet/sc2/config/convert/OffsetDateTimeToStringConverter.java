// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import java.time.OffsetDateTime;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class OffsetDateTimeToStringConverter
implements Converter<OffsetDateTime, String>
{

    @Override
    public String convert(OffsetDateTime src)
    {
        return src.toString();
    }

}
