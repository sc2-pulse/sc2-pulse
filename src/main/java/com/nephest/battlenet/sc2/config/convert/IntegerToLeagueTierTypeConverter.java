// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class IntegerToLeagueTierTypeConverter
implements Converter<Integer, BaseLeagueTier.LeagueTierType>
{

    @Override
    public BaseLeagueTier.LeagueTierType convert(Integer id)
    {
        return BaseLeagueTier.LeagueTierType.from(id);
    }

}
