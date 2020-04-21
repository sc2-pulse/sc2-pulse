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
        ss = new StatsService(null, null, null, null, null, null, null, null, null, null, null);
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
