// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import com.fasterxml.jackson.databind.util.StdConverter;
import java.util.Locale;

public class LocaleToBCP47StringConverter
extends StdConverter<Locale, String>
{

    @Override
    public String convert(Locale locale)
    {
        return locale.toLanguageTag();
    }

}
