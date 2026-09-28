// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class SortToStringConverter
implements Converter<Sort, String>
{

    @Override
    public String convert(Sort source)
    {
        if(source.isUnsorted()) return null;

        return source.stream()
            .map(order->order.isDescending()
                ? StringToSortConverter.PREFIX_DESC + order.getProperty()
                : order.getProperty())
            .collect(Collectors.joining(StringToSortConverter.DELIMITER));
    }

}
