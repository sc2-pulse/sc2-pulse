// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.web.util.Level;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

public class Status
{

    public enum Flag
    {

        OPERATIONAL("operational", "Fully operational.", Level.SUCCESS),
        PARTIAL("partial", "No BattleTags, league tiers, and clan names. "
            + "Simplified races in team formats.", Level.WARNING),
        REDIRECTED("redirected", "May be slower in some cases. Arbitrary data may be missing.", Level.WARNING),
        PRIORITIZED
        (
            "prioritized",
            "1v1 ladders are prioritized, other ladders are updated less frequently.",
            Level.WARNING
        ),
        WEB
        (
            "web",
            "Much slower.",
            Level.DANGER
        );

        private final String name;
        private final String description;
        private final Level level;

        Flag(String name, String description, Level level)
        {
            this.name = name;
            this.description = description;
            this.level = level;
        }

        public String getName()
        {
            return name;
        }

        public String getDescription()
        {
            return description;
        }

        public Level getLevel()
        {
            return level;
        }

    }

    private final Set<Flag> flags = EnumSet.noneOf(Flag.class);
    private Duration refreshDuration;

    public Status(){}

    public Set<Flag> getFlags()
    {
        return flags;
    }

    public Duration getRefreshDuration()
    {
        return refreshDuration;
    }

    public void setRefreshDuration(Duration refreshDuration)
    {
        this.refreshDuration = refreshDuration;
    }

}
