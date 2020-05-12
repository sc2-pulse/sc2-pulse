// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import com.nephest.battlenet.sc2.model.Identifiable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class IdentifiableToIntegerConverter
implements Converter<Identifiable, Integer>
{

    @Override
    public Integer convert(Identifiable identifiable)
    {
        return identifiable.getId();
    }

}

