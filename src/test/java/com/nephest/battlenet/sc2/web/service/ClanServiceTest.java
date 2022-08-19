// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ClanServiceTest
{

    @Mock
    private ClanDAO clanDAO;

    @Mock
    private VarDAO varDAO;

    private ClanService clanService;

    @BeforeEach
    public void beforeEach()
    {
        clanService = new ClanService(clanDAO, varDAO);
    }

    @Test
    public void testNullifyStats()
    {
        clanService.getNullifyStatsTask().setValue(Instant.now()
            .minus(ClanService.STATS_UPDATE_FRAME)
            .plusSeconds(1));
        clanService.update();
        verify(clanDAO, never()).nullifyStats(anyInt());

        Instant beforeStart = Instant.now();
        clanService.getNullifyStatsTask().setValue(Instant.now().minus(ClanService.STATS_UPDATE_FRAME));
        clanService.update();
        verify(clanDAO).nullifyStats(ClanDAO.CLAN_STATS_MIN_MEMBERS - 1);
        assertTrue(beforeStart.isBefore(clanService.getNullifyStatsTask().getValue()));
    }

    @Test
    public void testUpdateStats()
    {
        when(clanDAO.getCountByMinMemberCount(ClanDAO.CLAN_STATS_MIN_MEMBERS)).thenReturn(100);
        List<Integer> firstList = List.of(22);
        when(clanDAO.findIdsByMinMemberCount(ClanDAO.CLAN_STATS_MIN_MEMBERS, 0, 50))
            .thenReturn(firstList);
        clanService.getStatsUpdated().setValue(Instant.now().minus(ClanService.STATS_UPDATE_FRAME.dividedBy(2)));
        Instant beforeStart = Instant.now();

        clanService.update();
        verify(clanDAO).updateStats(firstList);
        assertTrue(beforeStart.isBefore(clanService.getStatsUpdated().getValue()));
        assertEquals(22, clanService.getStatsCursor().getValue());

        List<Integer> secondList = List.of();
        when(clanDAO.findIdsByMinMemberCount(ClanDAO.CLAN_STATS_MIN_MEMBERS, 22, 50))
            .thenReturn(secondList);
        clanService.getStatsUpdated().setValue(Instant.now().minus(ClanService.STATS_UPDATE_FRAME.dividedBy(2)));
        beforeStart = Instant.now();

        //The cursor is reset due to end of cursor
        clanService.update();
        assertTrue(beforeStart.isBefore(clanService.getStatsUpdated().getValue()));
        assertEquals(0, clanService.getStatsCursor().getValue());
    }

    @Test
    public void testUpdateStatsNoValidClans()
    {
        when(clanDAO.getCountByMinMemberCount(ClanDAO.CLAN_STATS_MIN_MEMBERS)).thenReturn(0);
        clanService.getStatsCursor().setValue(33L);

        //cursor was not reset
        clanService.update();
        assertEquals(33, clanService.getStatsCursor().getValue());
    }

}
