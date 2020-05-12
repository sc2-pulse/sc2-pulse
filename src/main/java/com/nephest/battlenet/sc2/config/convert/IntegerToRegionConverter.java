// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import com.nephest.battlenet.sc2.model.Region;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class IntegerToRegionConverter
implements Converter<Integer, Region>
{

    @Override
    public Region convert(Integer id)
    {
        return Region.from(id);
    }

}
