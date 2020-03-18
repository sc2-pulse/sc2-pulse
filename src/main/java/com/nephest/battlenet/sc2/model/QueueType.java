/*-
 * =========================LICENSE_START=========================
 * SC2 Ladder Generator
 * %%
 * Copyright (C) 2020 Oleksandr Masniuk
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END=========================
 */
package com.nephest.battlenet.sc2.model;

import static com.nephest.battlenet.sc2.model.TeamFormat.ARCHON;
import static com.nephest.battlenet.sc2.model.TeamFormat._1V1;
import static com.nephest.battlenet.sc2.model.TeamFormat._2V2;
import static com.nephest.battlenet.sc2.model.TeamFormat._3V3;
import static com.nephest.battlenet.sc2.model.TeamFormat._4V4;
import static com.nephest.battlenet.sc2.model.Version.HOTS;
import static com.nephest.battlenet.sc2.model.Version.LOTV;
import static com.nephest.battlenet.sc2.model.Version.WOL;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonValue;

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

    private static Map<Version, Set<QueueType>> VERSION_QUEUES;
    private static Map<TeamFormat, Set<QueueType>> TEAM_QUEUES;

    private final int id;
    private final Version version;
    private final TeamFormat teamFormat;

    static
    {
        Map<Version, Set<QueueType>> ver = new EnumMap(Version.class);
        Map<TeamFormat, Set<QueueType>> team = new EnumMap(TeamFormat.class);

        for (Version v : Version.values()) ver.put(v, new HashSet());
        for (TeamFormat t : TeamFormat.values()) team.put(t, new HashSet());

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

    private QueueType(int id, Version version, TeamFormat teamFormat)
    {
        this.id = id;
        this.version = version;
        this.teamFormat = teamFormat;
    }

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
