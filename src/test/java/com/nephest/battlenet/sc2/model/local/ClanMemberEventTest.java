// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.util.TestUtil;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class ClanMemberEventTest
{

    @Test
    public void testUniqueness()
    {
        OffsetDateTime equalOdt = OffsetDateTime.now();
        OffsetDateTime notEqualOdt = equalOdt.minusSeconds(1);
        ClanMemberEvent evt =
            new ClanMemberEvent(1L, 2, ClanMemberEvent.EventType.LEAVE, equalOdt, 3);
        ClanMemberEvent equalEvt =
            new ClanMemberEvent(1L, 3, ClanMemberEvent.EventType.JOIN, equalOdt, 4);
        ClanMemberEvent[] notEqualEvents = new ClanMemberEvent[]
        {
            new ClanMemberEvent(2L, 2, ClanMemberEvent.EventType.LEAVE, equalOdt, 3),
            new ClanMemberEvent(1L, 2, ClanMemberEvent.EventType.LEAVE, notEqualOdt, 3)
        };
        TestUtil.testUniqueness(evt, equalEvt, (Object[]) notEqualEvents);
    }

    @CsvSource
    ({
        "0, LEAVE",
        "1, JOIN"
    })
    @ParameterizedTest
    public void testFromId(Integer input, ClanMemberEvent.EventType expectedResult)
    {
        assertEquals(expectedResult, ClanMemberEvent.EventType.from(input));
    }

    @Test
    public void whenFromNullClan_thenLeaveEvent()
    {
        PlayerCharacter playerCharacter
            = new PlayerCharacter(1L, 2L, Region.EU, 3L, 4, "name#1");
        OffsetDateTime before = OffsetDateTime.now();
        ClanMemberEvent evt = ClanMemberEvent.from(playerCharacter, null);
        assertEquals(playerCharacter.getId(), evt.getPlayerCharacterId());
        assertNull(evt.getClanId());
        assertEquals(ClanMemberEvent.EventType.LEAVE, evt.getType());
        assertTrue(before.isBefore(evt.getCreated()));
    }

    @Test
    public void whenFromNotNullClan_thenJoinEvent()
    {
        PlayerCharacter playerCharacter
            = new PlayerCharacter(1L, 2L, Region.EU, 3L, 4, "name#1");
        Clan clan = new Clan(1, "tag", Region.EU, "name");
        OffsetDateTime before = OffsetDateTime.now();
        ClanMemberEvent evt = ClanMemberEvent.from(playerCharacter, clan);
        assertEquals(playerCharacter.getId(), evt.getPlayerCharacterId());
        assertEquals(clan.getId(), evt.getClanId());
        assertEquals(ClanMemberEvent.EventType.JOIN, evt.getType());
        assertTrue(before.isBefore(evt.getCreated()));
    }

}
