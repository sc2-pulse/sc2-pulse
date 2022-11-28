// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTeam;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTeamMember;
import com.nephest.battlenet.sc2.model.local.Division;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.LeagueTier;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.DivisionDAO;
import com.nephest.battlenet.sc2.model.local.dao.FastTeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.LeagueDAO;
import com.nephest.battlenet.sc2.model.local.dao.LeagueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.LeagueTierDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.PopulationStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.QueueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.ConversionService;
import org.springframework.validation.Validator;

@ExtendWith(MockitoExtension.class)
public class StatsServiceTest
{

    @Mock
    private StatsService ss;

    @Mock
    private TeamDAO teamDAO;

    @Mock
    private AlternativeLadderService alternativeLadderService;

    @Mock
    private BlizzardSC2API api;

    @Mock
    private SeasonDAO seasonDao;

    @Mock
    private LeagueDAO leagueDao;

    @Mock
    private LeagueTierDAO leagueTierDao;

    @Mock
    private DivisionDAO divisionDao;

    @Mock
    private FastTeamDAO fastTeamDAO;

    @Mock
    private TeamDAO teamDao;

    @Mock
    private TeamStateDAO teamStateDAO;

    @Mock
    private AccountDAO accountDao;

    @Mock
    private PlayerCharacterDAO playerCharacterDao;

    @Mock
    private ClanDAO clanDAO;

    @Mock
    private ClanMemberDAO clanMemberDAO;

    @Mock
    private TeamMemberDAO teamMemberDao;

    @Mock
    private QueueStatsDAO queueStatsDAO;

    @Mock
    private LeagueStatsDAO leagueStatsDao;

    @Mock
    private PlayerCharacterStatsDAO playerCharacterStatsDAO;

    @Mock
    private PopulationStateDAO populationStateDAO;

    @Mock
    private VarDAO varDAO;

    @Mock
    private SC2WebServiceUtil sc2WebServiceUtil;

    @Mock
    private ConversionService conversionService;

    private final ExecutorService dbExecutorService = Executors.newSingleThreadExecutor();

    @Mock
    private Validator validator;
    
    @Mock
    StatsService nss;

    @BeforeEach
    public void beforeEach()
    {
        ss = new StatsService
        (
            alternativeLadderService,
            api,
            seasonDao,
            leagueDao,
            leagueTierDao,
            divisionDao,
            teamDao,
            fastTeamDAO,
            teamStateDAO,
            accountDao,
            playerCharacterDao,
            clanDAO,
            clanMemberDAO,
            teamMemberDao,
            queueStatsDAO,
            leagueStatsDao,
            playerCharacterStatsDAO,
            populationStateDAO,
            varDAO,
            sc2WebServiceUtil,
            conversionService,
            validator,
            dbExecutorService
        );
        ss.setNestedService(nss);
    }

    @Test
    public void testInvalidTeam()
    {
        BlizzardTeam noMembersTeam = new BlizzardTeam();
        noMembersTeam.setWins(1);
        BlizzardTeam zeroGamesTeam = new BlizzardTeam();
        zeroGamesTeam.setMembers(new BlizzardTeamMember[]{new BlizzardTeamMember()});

        ss.updateTeams(new BlizzardTeam[]{noMembersTeam, zeroGamesTeam}, mock(Season.class),
            new League(1, 1, BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            mock(LeagueTier.class), mock(Division.class), Instant.now());

        verify(teamDAO, never()).merge(any());
    }

    @Test
    public void testStaleDataDetection()
    {
        ss.init();
        when(teamStateDAO.getCount(eq(Region.EU), any())).thenReturn(100);
        ss.checkStaleDataByTeamStateCount(Region.EU);
        assertFalse(ss.isAlternativeUpdate(Region.EU, true));

        //alternative, no matches found
        Instant before = Instant.now();
        when(teamStateDAO.getCount(eq(Region.EU), any())).thenReturn(0);
        ss.checkStaleDataByTeamStateCount(Region.EU);
        assertTrue(ss.isAlternativeUpdate(Region.EU, true));
        assertTrue(ss.getForcedAlternativeUpdateInstants().get(Region.EU).getValue().isAfter(before));

        //alternative, still no matches, nothing has changed
        ss.checkStaleDataByTeamStateCount(Region.EU);
        assertTrue(ss.isAlternativeUpdate(Region.EU, true));
        assertTrue(ss.getForcedAlternativeUpdateInstants().get(Region.EU).getValue().isAfter(before));

        //alternative, there are matches, but time has not passed yet
        when(teamStateDAO.getCount(eq(Region.EU), any())).thenReturn(100);
        ss.checkStaleDataByTeamStateCount(Region.EU);
        assertTrue(ss.isAlternativeUpdate(Region.EU, true));
        assertTrue(ss.getForcedAlternativeUpdateInstants().get(Region.EU).getValue().isAfter(before));

        //standard, there are matches and time has passed
        ss.getForcedAlternativeUpdateInstants().get(Region.EU).setValue(Instant.now()
            .minus(StatsService.FORCED_ALTERNATIVE_UPDATE_DURATION)
            .minusSeconds(1));
        ss.checkStaleDataByTeamStateCount(Region.EU);
        assertFalse(ss.isAlternativeUpdate(Region.EU, true));
        assertNull(ss.getForcedAlternativeUpdateInstants().get(Region.EU).getValue());
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
