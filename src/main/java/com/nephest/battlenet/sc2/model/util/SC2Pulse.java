// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.util;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * This util class contains methods that create objects with SC2Pulse specific settings.
 * Such objects are guaranteed to be lossless when passing them between different services.
 * You MUST use these methods if you wish to use listed classes in the SC2Pulse project.
 */
public final class SC2Pulse
{

    public static final ChronoUnit CHRONO_UNIT_MIN = ChronoUnit.MICROS;
    public static final Clock CLOCK
        = Clock.tick(Clock.systemUTC(), Duration.of(1, CHRONO_UNIT_MIN));

    private SC2Pulse(){}

    public static OffsetDateTime offsetDateTime()
    {
        return OffsetDateTime.now(CLOCK);
    }

    public static OffsetDateTime offsetDateTime(OffsetDateTime original)
    {
        return original.truncatedTo(CHRONO_UNIT_MIN);
    }

    public static OffsetDateTime offsetDateTime(int year, int month, int day)
    {
        return offsetDateTime(OffsetDateTime.of(year, month, day, 0, 0, 0, 0, ZoneOffset.UTC));
    }

    public static ZonedDateTime zonedDateTime()
    {
        return ZonedDateTime.now(CLOCK);
    }

    public static ZonedDateTime zonedDateTime(ZonedDateTime original)
    {
        return original.truncatedTo(CHRONO_UNIT_MIN);
    }

    public static Instant instant()
    {
        return Instant.now(CLOCK);
    }

    public static Instant instant(Instant original)
    {
        return original.truncatedTo(CHRONO_UNIT_MIN);
    }

}
