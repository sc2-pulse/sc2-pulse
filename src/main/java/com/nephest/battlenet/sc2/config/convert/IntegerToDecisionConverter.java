// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import com.nephest.battlenet.sc2.model.BaseMatch;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class IntegerToDecisionConverter
implements Converter<Integer, BaseMatch.Decision>
{

    @Override
    public BaseMatch.Decision convert(@NonNull Integer id)
    {
        return BaseMatch.Decision.from(id);
    }

}
