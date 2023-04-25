// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import com.nephest.battlenet.sc2.model.local.AccountProperty;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class IntegerToAccountPropertyTypeConverter
implements Converter<Integer, AccountProperty.PropertyType>
{

    @Override
    public AccountProperty.PropertyType convert(@NonNull Integer id)
    {
        return AccountProperty.PropertyType.from(id);
    }

}
