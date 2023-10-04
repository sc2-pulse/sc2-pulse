// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLegacyProfile;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.ClanMember;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanMemberEventDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.service.EventService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@ExtendWith(MockitoExtension.class)
public class ClanServiceTest
{

    @Mock
    private PlayerCharacterDAO playerCharacterDAO;

    @Mock
    private ClanDAO clanDAO;

    @Mock
    private ClanMemberDAO clanMemberDAO;

    @Mock
    private ClanMemberEventDAO clanMemberEventDAO;

    @Mock
    private BlizzardSC2API api;
    
    @Mock
    private EventService eventService;

    @Mock
    private AlternativeLadderService alternativeLadderService;

    @Mock
    private VarDAO varDAO;

    @Mock
    private ExecutorService executor;

    @Captor
    private ArgumentCaptor<Set<ClanMember>> clanMemberCaptor;

    private ClanService clanService;
    private ClanService nestedClanService;
    private Sinks.Many<Boolean> ladderUpdateEvent;

    @BeforeEach
    public void beforeEach()
    {
        lenient().when(executor.submit(any(Runnable.class))).then(i->{
            Runnable r = i.getArgument(0);
            r.run();
            return CompletableFuture.completedFuture(null);
        });
        lenient().when(executor.submit(any(Runnable.class), any())).then(i->{
            Runnable r = i.getArgument(0);
            r.run();
            return CompletableFuture.completedFuture(null);
        });
        ladderUpdateEvent = Sinks.unsafe().many().multicast().onBackpressureBuffer(10);
        when(eventService.getLadderUpdateEvent()).thenReturn(ladderUpdateEvent.asFlux());
        clanService = new ClanService
        (
            playerCharacterDAO,
            clanDAO,
            clanMemberDAO,
            clanMemberEventDAO,
            varDAO,
            api,
            eventService,
            alternativeLadderService,
            executor,
            executor
        );
        nestedClanService = spy(clanService);
        clanService.setClanService(nestedClanService);
    }
    
    private void update()
    {
        ladderUpdateEvent.emitNext(true, Sinks.EmitFailureHandler.FAIL_FAST);
    }

    @Test
    public void testNullifyStats()
    {
        clanService.getNullifyStatsTask().setValue(Instant.now()
            .minus(ClanService.STATS_UPDATE_FRAME)
            .plusSeconds(1));
        update();
        verify(clanDAO, never()).nullifyStats(anyInt());

        Instant beforeStart = Instant.now();
        clanService.getNullifyStatsTask().setValue(Instant.now().minus(ClanService.STATS_UPDATE_FRAME));
        update();
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

        update();
        verify(clanDAO).updateStats(firstList);
        assertTrue(beforeStart.isBefore(clanService.getStatsUpdated().getValue()));
        assertEquals(22, clanService.getStatsCursor().getValue());

        List<Integer> secondList = List.of();
        when(clanDAO.findIdsByMinMemberCount(ClanDAO.CLAN_STATS_MIN_MEMBERS, 22, 50))
            .thenReturn(secondList);
        clanService.getStatsUpdated().setValue(Instant.now().minus(ClanService.STATS_UPDATE_FRAME.dividedBy(2)));
        beforeStart = Instant.now();

        //The cursor is reset due to end of cursor
        update();
        assertTrue(beforeStart.isBefore(clanService.getStatsUpdated().getValue()));
        assertEquals(0, clanService.getStatsCursor().getValue());
    }

    @Test
    public void testUpdateStatsNoValidClans()
    {
        when(clanDAO.getCountByMinMemberCount(ClanDAO.CLAN_STATS_MIN_MEMBERS)).thenReturn(0);
        clanService.getStatsCursor().setValue(33L);

        //cursor was not reset
        update();
        assertEquals(33, clanService.getStatsCursor().getValue());
    }

    @Test
    public void testUpdateInactiveClanMembers()
    {
        List<PlayerCharacter> inactiveMembers = List.of
        (
            new PlayerCharacter(1L, 1L, Region.EU, 1L, 1, "name#1"),
            new PlayerCharacter(2L, 2L, Region.EU, 2L, 2, "name#2")
        );
        Flux<Tuple2<BlizzardLegacyProfile, PlayerCharacterNaturalId>> apiData = Flux.just
        (
            //clan membership should be updated
            Tuples.of
            (
                new BlizzardLegacyProfile(1L, 1, "name#1", "clan1", "clanName1"),
                inactiveMembers.get(0)
            ),
            //no clan, clan membership should be dropped
            Tuples.of
            (
                new BlizzardLegacyProfile(2L, 2, "name#2", null, null),
                inactiveMembers.get(1)
            )
        );
        when(clanMemberDAO.getInactiveCount(any())).thenReturn(inactiveMembers.size());
        when(playerCharacterDAO.findInactiveClanMembers(any(), eq(Long.MAX_VALUE), eq(inactiveMembers.size())))
            .thenReturn(inactiveMembers);
        when(api.getLegacyProfiles(inactiveMembers, false)).thenReturn(apiData);
        when(clanDAO.merge(any())).thenAnswer(inv->{
            for(int i = 0; i < inv.getArguments().length; i++)
                ((Clan)(inv.getArgument(i, Set.class).iterator().next())).setId(i);
            return Set.of();
        });
        clanService.getInactiveClanMembersUpdated().setValue(Instant.now().minus(ClanService.CLAN_MEMBER_UPDATE_FRAME));

        Instant beforeUpdate = Instant.now();
        update();
        verify(clanMemberDAO).removeExpired();
        //clan membership dropped
        verify(clanMemberDAO).remove(Set.of(2L));
        verify(clanMemberDAO).merge(clanMemberCaptor.capture());
        assertEquals(1, clanMemberCaptor.getAllValues().size());

        //clan membership updated
        ClanMember cm1 = clanMemberCaptor.getValue().iterator().next();
        assertEquals(inactiveMembers.get(0).getId(), cm1.getPlayerCharacterId());
        assertEquals(0, cm1.getClanId());

        //vars were updated
        assertTrue(beforeUpdate.isBefore(clanService.getInactiveClanMembersUpdated().getValue()));
        assertEquals(inactiveMembers.get(1).getId(), clanService.getInactiveClanMembersCursor().getValue());
    }

    @Test
    public void whenNoInactiveClanMembers_thenDontUpdateVars()
    {
        Instant beforeUpdate = Instant.now().minus(ClanService.CLAN_MEMBER_UPDATE_FRAME);
        when(clanMemberDAO.getInactiveCount(any())).thenReturn(0);
        clanService.getInactiveClanMembersCursor().setValue(33L);
        clanService.getInactiveClanMembersUpdated().setValue(beforeUpdate);

        update();

        assertEquals(beforeUpdate, clanService.getInactiveClanMembersUpdated().getValue());
        assertEquals(33L, clanService.getInactiveClanMembersCursor().getValue());
    }

    @Test
    public void whenEmptyInactiveClanMemberBatch_thenResetIdCursor()
    {
        Instant beforeUpdate = Instant.now().minus(ClanService.CLAN_MEMBER_UPDATE_FRAME);
        when(clanMemberDAO.getInactiveCount(any())).thenReturn(2);
        clanService.getInactiveClanMembersCursor().setValue(33L);
        clanService.getInactiveClanMembersUpdated().setValue(beforeUpdate);

        update();

        assertEquals(beforeUpdate, clanService.getInactiveClanMembersUpdated().getValue());
        assertEquals(Long.MAX_VALUE, clanService.getInactiveClanMembersCursor().getValue());
    }

}
