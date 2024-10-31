// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.min;

import com.nephest.battlenet.sc2.model.Identifiable;
import org.springframework.core.convert.converter.Converter;

public class IdentifiableToMinimalObjectConverter
implements Converter<Identifiable, Object>
{

    @Override
    public Object convert(Identifiable source)
    {
        return source.getId();
    }

}
