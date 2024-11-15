// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.min;

import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;

public class TemporalAccessorToMinimalObjectConverter
implements Converter<TemporalAccessor, Object>
{

    public static final TemporalField TEMPORAL_FIELD = ChronoField.INSTANT_SECONDS;

    @Override
    public Object convert(@NotNull TemporalAccessor source)
    {
        if(!source.isSupported(TEMPORAL_FIELD)) return source;

        return source.getLong(TEMPORAL_FIELD);
    }

}
