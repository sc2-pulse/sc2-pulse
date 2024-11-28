// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class InstantVar
extends Var<Instant>
{

    public static final long MILLIS_SECONDS_MIN = -9223372036854776L;
    public static final ChronoUnit CHRONO_UNIT_MIN = ChronoUnit.MILLIS;
    public static final String STRING_PREFIX = "s";

    public InstantVar(VarDAO varDAO, String key, boolean load)
    {
        super
        (
            varDAO,
            key,
            InstantVar::convert,
            InstantVar::convert,
            load
        );
    }

    public InstantVar(VarDAO varDAO, String key)
    {
        this(varDAO, key, true);
    }

    private static String convert(Instant instant)
    {
        if(instant == null) return null;

        instant = instant.truncatedTo(CHRONO_UNIT_MIN);
        return instant.getEpochSecond() < MILLIS_SECONDS_MIN
            ? STRING_PREFIX + instant
            : String.valueOf(instant.toEpochMilli());
    }

    private static Instant convert(String str)
    {
        if(str == null || str.isEmpty()) return null;

        if(str.startsWith(STRING_PREFIX))
        {
            if(str.length() == STRING_PREFIX.length())
                throw new IllegalArgumentException("Invalid input: " + str);

            return Instant.parse(str.substring(STRING_PREFIX.length()));
        }

        return Instant.ofEpochMilli(Long.parseLong(str));
    }

}
