// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import com.nephest.battlenet.sc2.model.web.SortParameter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class SortParameterToStringConverter
implements Converter<SortParameter, String>
{

    @Override
    public String convert(SortParameter source)
    {
        return source.toPrefixedString();
    }

}
