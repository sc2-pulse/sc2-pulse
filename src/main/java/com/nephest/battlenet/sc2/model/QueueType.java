// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.*;

import static com.nephest.battlenet.sc2.model.TeamFormat.*;
import static com.nephest.battlenet.sc2.model.Version.*;

public enum QueueType
implements Identifiable
{

    WOL_1V1(1, WOL, _1V1),
    WOL_2V2(2, WOL, _2V2),
    WOL_3V3(3, WOL, _3V3),
    WOL_4V4(4, WOL, _4V4),

    HOTS_1V1(101, HOTS, _1V1),
    HOTS_2V2(102, HOTS, _2V2),
    HOTS_3V3(103, HOTS, _3V3),
    HOTS_4V4(104, HOTS, _4V4),

    LOTV_1V1(201, LOTV, _1V1),
    LOTV_2V2(202, LOTV, _2V2),
    LOTV_3V3(203, LOTV, _3V3),
    LOTV_4V4(204, LOTV, _4V4),
    LOTV_ARCHON(206, LOTV, ARCHON);

    private static final Map<Version, Set<QueueType>> VERSION_QUEUES;
    private static final Map<TeamFormat, Set<QueueType>> TEAM_QUEUES;

    private final int id;
    private final Version version;
    private final TeamFormat teamFormat;

    static
    {
        Map<Version, Set<QueueType>> ver = new EnumMap<>(Version.class);
        Map<TeamFormat, Set<QueueType>> team = new EnumMap<>(TeamFormat.class);

        for (Version v : Version.values()) ver.put(v, EnumSet.noneOf(QueueType.class));
        for (TeamFormat t : TeamFormat.values()) team.put(t, EnumSet.noneOf(QueueType.class));

        for (QueueType q : QueueType.values())
        {
            ver.get(q.getVersion()).add(q);
            team.get(q.getTeamFormat()).add(q);
        }

        for(Map.Entry<Version, Set<QueueType>> entry : ver.entrySet())
            entry.setValue(Collections.unmodifiableSet(entry.getValue()));
        for(Map.Entry<TeamFormat, Set<QueueType>> entry : team.entrySet())
            entry.setValue(Collections.unmodifiableSet(entry.getValue()));

        VERSION_QUEUES = Collections.unmodifiableMap(ver);
        TEAM_QUEUES = Collections.unmodifiableMap(team);
    }

    QueueType(int id, Version version, TeamFormat teamFormat)
    {
        this.id = id;
        this.version = version;
        this.teamFormat = teamFormat;
    }

    @JsonCreator
    public static QueueType from(int id)
    {
        for (QueueType region : QueueType.values())
        {
            if (region.getId() == id) return region;
        }

        throw new IllegalArgumentException("Invalid id");
    }

    public static QueueType from(Version version, TeamFormat teamFormat)
    {
        for (QueueType region : QueueType.values())
        {
            if (region.getVersion() == version && region.getTeamFormat() == teamFormat) return region;
        }

        throw new IllegalArgumentException("Invalid version/team format combination");
    }

    public static Set<QueueType> getTypes(Version version)
    {
        return VERSION_QUEUES.get(version);
    }

    public static Set<QueueType> getTypes(TeamFormat teamFormat)
    {
        return TEAM_QUEUES.get(teamFormat);
    }

    @Override
    @JsonValue
    public int getId()
    {
        return id;
    }

    public Version getVersion()
    {
        return version;
    }

    public TeamFormat getTeamFormat()
    {
        return teamFormat;
    }

}
