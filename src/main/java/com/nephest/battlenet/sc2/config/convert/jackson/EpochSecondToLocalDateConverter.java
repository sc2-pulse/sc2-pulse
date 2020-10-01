// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import com.fasterxml.jackson.databind.util.StdConverter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public class EpochSecondToLocalDateConverter
extends StdConverter<Long, LocalDate>
{
    @Override
    public LocalDate convert(Long l)
    {
        return LocalDate.ofInstant(Instant.ofEpochSecond(l), ZoneOffset.UTC);
    }
}
