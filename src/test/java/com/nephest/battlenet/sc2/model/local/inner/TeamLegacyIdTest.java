// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.util.TestUtil;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class TeamLegacyIdTest
{

    @Test
    public void testUniqueness()
    {
        TestUtil.testUniqueness
        (
            TeamLegacyId.standard(List.of(
                new TeamLegacyIdEntry(1, 2L, Race.ZERG),
                new TeamLegacyIdEntry(3, 4L)
            )),
            TeamLegacyId.trusted("1.2.3~3.4."),

            TeamLegacyId.standard(List.of(
                new TeamLegacyIdEntry(1, 2L, Race.ZERG),
                new TeamLegacyIdEntry(3, 4L),
                new TeamLegacyIdEntry(5, 6L)
            )),
            TeamLegacyId.trusted("1.2.3~3.4.~5.6.")
        );
    }

    @Test
    public void whenCreatingTrusted_thenDontParseTheIdUntilEntryGetterIsCalled()
    {
        TeamLegacyId id = TeamLegacyId.trusted("invalidId");
        assertThrows(IllegalArgumentException.class, id::getEntries);
    }

    @Test
    public void testIdParsing()
    {
        TeamLegacyId id = TeamLegacyId.trusted("3.4.~1.2.3");
        assertEquals
        (
            List.of
            (
                new TeamLegacyIdEntry(3, 4L),
                new TeamLegacyIdEntry(1, 2L, Race.ZERG)
            ),
            id.getEntries()
        );
    }

    @Test
    public void whenCreatingStandard_thenEntriesAreCopiedIntoNewSortedList()
    {
        List<TeamLegacyIdEntry> unsortedOriginalIds = new ArrayList<>(List.of
        (
            new TeamLegacyIdEntry(1, 3L, Race.ZERG),
            new TeamLegacyIdEntry(3, 4L),
            new TeamLegacyIdEntry(1, 2L, Race.TERRAN)
        ));
        TeamLegacyId id = TeamLegacyId.standard(unsortedOriginalIds);
        assertEquals
        (
            List.of
            (
                unsortedOriginalIds.get(2),
                unsortedOriginalIds.get(0),
                unsortedOriginalIds.get(1)
            ),
            id.getEntries()
        );
        assertEquals("1.2.1~1.3.3~3.4.", id.getId());

        unsortedOriginalIds.add(unsortedOriginalIds.get(0));
        assertEquals(3, id.getEntries().size());
        assertThrows
        (
            UnsupportedOperationException.class,
            ()->id.getEntries().add(unsortedOriginalIds.get(0))
        );
    }


}
