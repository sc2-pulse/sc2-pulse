// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import com.fasterxml.jackson.databind.util.StdConverter;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class EpochMilliToOffsetDateTimeConverter
extends StdConverter<Long, OffsetDateTime>
{

    @Override
    public OffsetDateTime convert(Long l)
    {
        return SC2Pulse.offsetDateTime(OffsetDateTime.ofInstant(Instant.ofEpochMilli(l), ZoneOffset.UTC));
    }

}
