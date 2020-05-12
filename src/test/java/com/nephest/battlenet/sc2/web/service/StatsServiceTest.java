// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import org.junit.jupiter.api.BeforeEach;

import static org.mockito.Mockito.mock;

public class StatsServiceTest
{

    private StatsService ss;
    private StatsService nss;

    @BeforeEach
    public void beforeEach()
    {
        ss = new StatsService(null, null, null, null, null, null, null, null, null, null, null, null);
        nss = mock(StatsService.class);
        ss.setNestedService(nss);
    }
/*
    @Test
    public void testMemberTransaction()
    {
        BlizzardTierDivision[] bDivisions =
            {null, null, null, null, null, null, null, null, null, null, null, null};
        BlizzardLeagueTier bTier = new BlizzardLeagueTier();
        bTier.setDivisions(bDivisions);
        League league = new League();
        league.setQueueType(QueueType.LOTV_1V1);
        league.setTeamType(TeamType.ARRANGED);
        Season season = mock(Season.class);
        LeagueTier tier = mock(LeagueTier.class);

        ss.updateDivisions(bTier, season, league, tier);
        System.out.println(nss.toString());

        verify(nss).updateDivisions(bDivisions, season, league, tier, 0, 5);
        verify(nss).updateDivisions(bDivisions, season, league, tier, 5, 10);
        verify(nss).updateDivisions(bDivisions, season, league, tier, 10, 12);
        verifyNoMoreInteractions(nss);

        league.setQueueType(QueueType.LOTV_2V2);
        ss.updateDivisions(bTier, season, league, tier);
        verify(nss).updateDivisions(bDivisions, season, league, tier, 0, 2);
        verify(nss).updateDivisions(bDivisions, season, league, tier, 2, 4);
        verify(nss).updateDivisions(bDivisions, season, league, tier, 4, 6);
        verify(nss).updateDivisions(bDivisions, season, league, tier, 6, 8);
        verify(nss).updateDivisions(bDivisions, season, league, tier, 8, 10);
        //first invocation is from 1v1
        verify(nss, times(2)).updateDivisions(bDivisions, season, league, tier, 10, 12);
        verifyNoMoreInteractions(nss);

        bTier.setDivisions(new BlizzardTierDivision[0]);
        ss.updateDivisions(bTier, season, league, tier);
        verifyNoMoreInteractions(nss);
    }*/

}
