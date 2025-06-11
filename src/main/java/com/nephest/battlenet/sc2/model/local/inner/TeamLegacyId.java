// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class TeamLegacyId
implements Comparable<TeamLegacyId>, Serializable
{

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String DELIMITER = "~";

    @NotEmpty
    private List<TeamLegacyIdEntry> entries;

    @NotBlank
    private final String id;

    private TeamLegacyId(@NotBlank String id, List<TeamLegacyIdEntry> entries)
    {
        this.id = id;
        this.entries = entries;
    }

    public static TeamLegacyId standard(@NotEmpty Collection<TeamLegacyIdEntry> entries)
    {
        List<TeamLegacyIdEntry> sortedEntries = entries.stream().sorted().toList();
        return new TeamLegacyId(createId(sortedEntries), sortedEntries);
    }

    public static TeamLegacyId trusted(String id)
    {
        return new TeamLegacyId(id, null);
    }

    public static TeamLegacyId trusted(String id, @NotEmpty List<TeamLegacyIdEntry> entries)
    {
        return new TeamLegacyId(id, entries);
    }

    private static String createId(List<TeamLegacyIdEntry> entries)
    {
        return entries.stream()
            .map(TeamLegacyIdEntry::toLegacyIdSectionString)
            .collect(Collectors.joining(TeamLegacyId.DELIMITER));
    }

    private static List<TeamLegacyIdEntry> createEntries(String id)
    {
        return Arrays.stream(id.split(TeamLegacyId.DELIMITER))
            .map(TeamLegacyIdEntry::fromLegacyIdSectionString)
            .toList();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof TeamLegacyId that)) {return false;}
        return Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode()
    {
        return getId().hashCode();
    }

    @Override
    public String toString()
    {
        return "TeamLegacyId{" + "id='" + id + '\'' + '}';
    }

    @Override
    public int compareTo(@NotNull TeamLegacyId teamLegacyId)
    {
        return getId().compareTo(teamLegacyId.getId());
    }

    public String getId()
    {
        return id;
    }

    public List<TeamLegacyIdEntry> getEntries()
    {
        if(entries == null) entries = createEntries(getId());
        return entries;
    }

}
