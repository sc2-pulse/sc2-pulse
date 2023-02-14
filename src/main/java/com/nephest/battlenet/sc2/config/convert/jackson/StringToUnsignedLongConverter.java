// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import com.fasterxml.jackson.databind.util.StdConverter;

public class StringToUnsignedLongConverter
extends StdConverter<String, Long>
{

    @Override
    public Long convert(String s)
    {
        return Long.parseUnsignedLong(s);
    }

}