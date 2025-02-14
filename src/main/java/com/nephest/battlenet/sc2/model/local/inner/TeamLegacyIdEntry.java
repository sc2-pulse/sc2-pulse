// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import com.nephest.battlenet.sc2.model.Race;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.Objects;

public record TeamLegacyIdEntry(@NotNull Integer realm, @NotNull Long id, @Nullable Race race)
implements Comparable<TeamLegacyIdEntry>
{

    public static final String DELIMITER = ".";
    public static final String REGEXP_DELIMITER = "\\.";
    public static final Comparator<TeamLegacyIdEntry> COMPARATOR
        = Comparator.comparing(TeamLegacyIdEntry::realm)
            .thenComparing(TeamLegacyIdEntry::id);

    public TeamLegacyIdEntry(Integer realm, Long id, @Nullable Race race)
    {
        this.realm = realm;
        this.id = id;
        this.race = race;
    }

    public TeamLegacyIdEntry(Integer realm, Long id)
    {
        this(realm, id, null);
    }

    public static TeamLegacyIdEntry fromLegacyIdSectionString(String text)
    {
        String[] sections = text.split(REGEXP_DELIMITER);
        if(sections.length < 2 || sections.length > 3)
            throw new IllegalArgumentException("2-3 sections expected");

        return new TeamLegacyIdEntry
        (
            Integer.parseInt(sections[0]),
            Long.parseLong(sections[1]),
            sections.length == 3 ? Race.from(Integer.parseInt(sections[2])) : null
        );
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof TeamLegacyIdEntry that)) {return false;}
        return Objects.equals(realm(), that.realm())
            && Objects.equals(id(), that.id());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(realm(), id());
    }

    @Override
    public String toString()
    {
        return "TeamLegacyIdEntry{" + "id=" + id + ", realm=" + realm + '}';
    }

    public String toLegacyIdSectionString()
    {
        return appendLegacyIdSectionStringTo(new StringBuilder()).toString();
    }

    public StringBuilder appendLegacyIdSectionStringTo(StringBuilder sb)
    {
        sb.append(realm()).append(DELIMITER).append(id()).append(DELIMITER);
        if (race() != null) {sb.append(race().getId());}

        return sb;
    }

    @Override
    public int compareTo(@NotNull TeamLegacyIdEntry teamLegacyIdEntry)
    {
        return COMPARATOR.compare(this, teamLegacyIdEntry);
    }

}
