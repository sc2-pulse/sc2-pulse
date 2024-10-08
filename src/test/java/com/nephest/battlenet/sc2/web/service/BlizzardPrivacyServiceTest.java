// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static com.nephest.battlenet.sc2.web.service.BlizzardPrivacyService.REQUEST_LIMIT_PRIORITY_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardAccount;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardClan;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLadder;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLadderLeague;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLeague;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLeagueTier;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLegacyProfile;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardPlayerCharacter;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardProfileLadder;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardProfileTeam;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardProfileTeamMember;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardSeason;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTeam;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTeamMember;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTeamMemberRace;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTierDivision;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.local.inner.AccountCharacterData;
import com.nephest.battlenet.sc2.model.local.inner.ClanMemberEventData;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.util.MiscUtil;
import com.nephest.battlenet.sc2.util.TestUtil;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.validation.Validator;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

@ExtendWith(MockitoExtension.class)
public class BlizzardPrivacyServiceTest
{

    @Mock
    private BlizzardSC2API api;

    @Mock
    private StatsService statsService;

    @Mock
    private AlternativeLadderService alternativeLadderService;

    @Mock
    private SeasonDAO seasonDAO;

    @Mock
    private VarDAO varDAO;

    @Mock
    private PlayerCharacterDAO playerCharacterDAO;

    @Mock
    private AccountDAO accountDAO;

    @Mock
    private Validator validator;

    @Mock
    private ClanService clanService;

    @Mock
    private SC2WebServiceUtil sc2WebServiceUtil;

    @Captor
    private ArgumentCaptor<Set<AccountCharacterData>> accountPlayerCaptor;

    @Captor
    private ArgumentCaptor<Collection<ClanMemberEventData>> clanDataCaptor;

    @Captor
    private ArgumentCaptor<OffsetDateTime> offsetDateTimeArgumentCaptor;

    @Captor
    private ArgumentCaptor<Set<PlayerCharacter>> characterArgumentCaptor;

    @Mock
    private ExecutorService executor;

    public BlizzardPrivacyService privacyService;

    private GlobalContext globalContext;

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
        lenient().doAnswer(i->{
            Runnable r = i.getArgument(0);
            r.run();
            return null;
        }).when(executor).execute(any(Runnable.class));
        globalContext = new GlobalContext(Set.of(Region.EU, Region.US, Region.KR));
        privacyService = new BlizzardPrivacyService
        (
            api,
            statsService,
            alternativeLadderService,
            seasonDAO,
            varDAO,
            accountDAO,
            playerCharacterDAO,
            clanService,
            executor, Schedulers.immediate(), executor,
            validator,
            sc2WebServiceUtil,
            globalContext,
            true
        );
    }

    @Test
    public void testGetSeasonToUpdate()
    throws ExecutionException, InterruptedException
    {
        privacyService = new BlizzardPrivacyService
        (
            api,
            statsService,
            alternativeLadderService,
            seasonDAO,
            varDAO,
            accountDAO,
            playerCharacterDAO,
            clanService,
            TestUtil.EXECUTOR_SERVICE, Schedulers.immediate(), TestUtil.EXECUTOR_SERVICE,
            validator,
            sc2WebServiceUtil,
            globalContext,
            true
        );
        when(statsService.isAlternativeUpdate(any(), anyBoolean())).thenReturn(false);
        when(sc2WebServiceUtil.getExternalOrExistingSeason(any(), anyInt())).thenReturn(new BlizzardSeason());
        stubLadderApi();
        //there ate no seasons in the db, nothing to update
        assertNull(privacyService.getSeasonToUpdate());

        //there are seasons in the db, current season update is prioritized
        when(seasonDAO.getMaxBattlenetId()).thenReturn(BlizzardSC2API.FIRST_SEASON + 2);
        assertEquals(BlizzardSC2API.FIRST_SEASON + 2, privacyService.getSeasonToUpdate());

        privacyService.updateOldSeasons();
        privacyService.getUpdateOldDataTask().get();

        long updateTimeFrame = BlizzardPrivacyService.DATA_TTL
            .dividedBy(BlizzardPrivacyService.CURRENT_SEASON_UPDATES_PER_PERIOD + 2) //+ 2 existing seasons
            .toSeconds();

        long currentUpdateTimeFrame = BlizzardPrivacyService.DATA_TTL
            .dividedBy(BlizzardPrivacyService.CURRENT_SEASON_UPDATES_PER_PERIOD)
            .toSeconds();

        //rewind update timestamp to simulate the time flow
        privacyService.getLastUpdatedSeasonInstantVar().setValue(SC2Pulse.instant().minusSeconds(updateTimeFrame));
        assertEquals(BlizzardSC2API.FIRST_SEASON, privacyService.getSeasonToUpdate());
        privacyService.updateOldSeasons();
        privacyService.getUpdateOldDataTask().get();

        //next season is updated
        assertEquals(BlizzardSC2API.FIRST_SEASON + 1, privacyService.getSeasonToUpdate());
        privacyService.updateOldSeasons();
        privacyService.getUpdateOldDataTask().get();

        //all previous season were updated, starting from the first season again
        assertEquals(BlizzardSC2API.FIRST_SEASON, privacyService.getSeasonToUpdate());
        privacyService.updateOldSeasons();
        privacyService.getUpdateOldDataTask().get();

        //current season update is prioritized
        privacyService.getLastUpdatedCurrentSeasonInstantVar().setValue(SC2Pulse.instant().minusSeconds(currentUpdateTimeFrame));
        assertEquals(BlizzardSC2API.FIRST_SEASON + 2, privacyService.getSeasonToUpdate());
        privacyService.updateOldSeasons();
        privacyService.getUpdateOldDataTask().get();
    }

    @Test
    public void testAnonymizeExpiredData()
    {
        privacyService.update();

        OffsetDateTime anonymizeOffset = OffsetDateTime.of(2015, 1, 1, 0, 0, 0, 0, SC2Pulse.offsetDateTime().getOffset());
        InOrder order = inOrder(accountDAO, playerCharacterDAO);
        order.verify(accountDAO).removeEmptyAccounts();
        order.verify(accountDAO, times(2)).anonymizeExpiredAccounts(offsetDateTimeArgumentCaptor.capture());
        //full anonymization
        assertTrue(BlizzardPrivacyService.DEFAULT_ANONYMIZE_START.isEqual(offsetDateTimeArgumentCaptor.getAllValues().get(0)));
        //partial anonymization
        assertTrue(anonymizeOffset.isEqual(offsetDateTimeArgumentCaptor.getAllValues().get(1)));
        order.verify(playerCharacterDAO).anonymizeExpiredCharacters(argThat(m->m.isEqual(anonymizeOffset)));
    }

    @Test
    public void testUpdateCharacters()
    {
        privacyService.getLastUpdatedCharacterId().setValue(10L);
        when(playerCharacterDAO.countByUpdatedMax(any(), any())).thenReturn(0);
        privacyService.update();
        //reset id cursor due to empty batch size
        assertEquals(Long.MAX_VALUE, privacyService.getLastUpdatedCharacterId().getValue());

        //rewind
        privacyService.getLastUpdatedCharacterInstant()
            .setValue(SC2Pulse.instant().minus(BlizzardPrivacyService.CHARACTER_UPDATE_TIME_FRAME).minusSeconds(1));

        when(playerCharacterDAO.countByUpdatedMax(any(), any())).thenReturn(9999);
        privacyService.getLastUpdatedCharacterId().setValue(100L);
        List<PlayerCharacter> chars = List.of(new PlayerCharacter(null, null, Region.EU, null, null, null));
        when(playerCharacterDAO.find
        (
            any(), eq(100L), any(),
            eq((int) Math.ceil(9999d / BlizzardPrivacyService.CHARACTER_UPDATES_PER_TTL)))
        )
            .thenReturn(chars);
        BlizzardLegacyProfile profile1 = new BlizzardLegacyProfile(1L, 2, "name", "clanTag", null);
        PlayerCharacter character1 = new PlayerCharacter(null, null, Region.EU, 1L, 2, "name");
        BlizzardLegacyProfile profile2 = new BlizzardLegacyProfile(2L, 2, "name", null, null);
        PlayerCharacter character2 = new PlayerCharacter(null, null, Region.EU, 2L, 2, "name");
        when(api.getLegacyProfiles(chars, false))
            .thenReturn(Flux.just(Tuples.of(profile1, character1), Tuples.of(profile2, character2)));

        privacyService.update();
        verify(playerCharacterDAO).updateCharacters(characterArgumentCaptor.capture());
        List<PlayerCharacter> argChars = characterArgumentCaptor
            .getAllValues()
            .stream()
            .flatMap(Collection::stream)
            .sorted(PlayerCharacter.NATURAL_ID_COMPARATOR)
            .collect(Collectors.toList());
        assertEquals(2, argChars.size());
        assertEquals(character1, argChars.get(0));
        assertEquals(character2, argChars.get(1));
    }

    @Test
    public void testUpdateCharactersEmptyBatch()
    {
        privacyService.getLastUpdatedCharacterId().setValue(10L);
        when(playerCharacterDAO.countByUpdatedMax(any(), any())).thenReturn(9999);
        when(playerCharacterDAO.find(any(), any(), any(), anyInt())).thenReturn(List.of());
        privacyService.update();
        //reset id cursor due to empty batch
        assertEquals(Long.MAX_VALUE, privacyService.getLastUpdatedCharacterId().getValue());
    }

    @Test
    public void whenUpdateCharacterProfilesIsDisabled_thenDontUpdateCharacters()
    {
        privacyService = new BlizzardPrivacyService
        (
            api,
            statsService,
            alternativeLadderService,
            seasonDAO,
            varDAO,
            accountDAO,
            playerCharacterDAO,
            clanService,
            executor, Schedulers.immediate(), executor,
            validator,
            sc2WebServiceUtil,
            globalContext,
            false
        );
        lenient().when(playerCharacterDAO.countByUpdatedMax(any(), any()))
            .thenThrow(new IllegalStateException("test"));
        privacyService.update();
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    public void testUpdateAlternativeLadder(boolean charUpdated)
    {
        when(seasonDAO.getMaxBattlenetId()).thenReturn(BlizzardSC2API.FIRST_SEASON);
        when(statsService.isAlternativeUpdate(any(), anyBoolean())).thenReturn(true);
        BlizzardProfileLadder ladder = new BlizzardProfileLadder
        (
            new BlizzardProfileTeam[]
            {
                new BlizzardProfileTeam
                (
                    new BlizzardProfileTeamMember[]
                    {
                        new BlizzardProfileTeamMember(1L, 1, "name1", Race.TERRAN, "clan1"),
                        new BlizzardProfileTeamMember(2L, 1, "name2", Race.TERRAN, null)
                    },
                    0L, 0, 0, 0, 0
                )
            },
            null
        );
        Flux<Tuple2<BlizzardProfileLadder, Tuple3<Region, BlizzardPlayerCharacter[], Long>>> ladders = Flux.just
        (
            Tuples.of
            (
                ladder,
                Tuples.of
                (
                    Region.EU,
                    new BlizzardPlayerCharacter[0],
                    0L
                )
            )
        );
        OngoingStubbing<Flux<Tuple2<BlizzardProfileLadder, Tuple3<Region, BlizzardPlayerCharacter[], Long>>>> stub =
            when(api.getProfileLadders(any(), any(), anyBoolean(), eq(REQUEST_LIMIT_PRIORITY_NAME)))
                .thenReturn(ladders);
        for(int i = 1; i < globalContext.getActiveRegions().size(); i++)
            stub = stub.thenReturn(Flux.empty());
        if(charUpdated) when(playerCharacterDAO.updateCharacters(any())).thenAnswer(i->{
            Set<PlayerCharacter> chars = i.getArgument(0);
            chars.forEach(c->c.setId(c.getBattlenetId()));
            return chars;
        });
        privacyService.updateOldSeasons();

        PlayerCharacter character1 = new PlayerCharacter(1L, null, Region.EU, 1L, 1, "name1");
        PlayerCharacter character2 = new PlayerCharacter(2L, null, Region.EU, 2L, 1, "name2");
        verify(playerCharacterDAO).updateCharacters(characterArgumentCaptor.capture());
        List<PlayerCharacter> argChars = characterArgumentCaptor.getAllValues()
            .stream()
            .flatMap(Collection::stream)
            .sorted(PlayerCharacter.NATURAL_ID_COMPARATOR)
            .collect(Collectors.toList());
        assertEquals(2, argChars.size());
        assertEquals(character1, argChars.get(0));
        assertEquals(character2, argChars.get(1));

        verify(clanService).saveClans(clanDataCaptor.capture());
        Assertions.assertThat(clanDataCaptor.getValue())
            .usingRecursiveComparison()
            .isEqualTo(!charUpdated ? List.of() : List.of(
                new ClanMemberEventData
                (
                    new PlayerCharacter(1L, null, Region.EU, 1L, 1, "name1#1"),
                    new Clan(null, "clan1", Region.EU, null),
                    ladder.getCreatedAt()
                ),
                new ClanMemberEventData
                (
                    new PlayerCharacter(2L, null, Region.EU, 2L, 1, "name2#1"),
                    null,
                    ladder.getCreatedAt()
                )
            ));
    }

    @CsvSource
    ({"true", "false"})
    @ParameterizedTest
    public void testUpdateLadder(boolean updated)
    {
        when(seasonDAO.getMaxBattlenetId()).thenReturn(BlizzardSC2API.FIRST_SEASON + 1);
        when(statsService.isAlternativeUpdate(any(), eq(true))).thenReturn(!updated);
        when(statsService.isAlternativeUpdate(any(), eq(false))).thenReturn(false);
        when(sc2WebServiceUtil.getExternalOrExistingSeason(any(), anyInt())).thenReturn(new BlizzardSeason());

        //update previous season
        privacyService.getLastUpdatedCurrentSeasonInstantVar().setValue(SC2Pulse.instant());
        stubLadderApi();

        privacyService.updateOldSeasons();

        verify(playerCharacterDAO).updateAccountsAndCharacters(accountPlayerCaptor.capture());
        verify(clanService, never()).saveClans(any());
        Set<AccountCharacterData> argChars = accountPlayerCaptor.getValue();
        PlayerCharacter character1 = new PlayerCharacter(null, null, Region.EU, 1L, 1, "name1");
        assertEquals(1, argChars.size());
        AccountCharacterData acData = argChars.iterator().next();
        PlayerCharacter extractedCharacter = acData.getCharacter();
        assertEquals(character1, extractedCharacter);
        assertEquals("name2#1", extractedCharacter.getName());
        //false because season is not the current season
        assertFalse(acData.isFresh());
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    public void testUpdateCurrentLadder(boolean charUpdated)
    {
        when(seasonDAO.getMaxBattlenetId()).thenReturn(BlizzardSC2API.FIRST_SEASON + 1);
        when(statsService.isAlternativeUpdate(any(), anyBoolean())).thenReturn(false);
        when(sc2WebServiceUtil.getExternalOrExistingSeason(any(), anyInt())).thenReturn(new BlizzardSeason());
        if(charUpdated) when(playerCharacterDAO.updateAccountsAndCharacters(any()))
            .thenAnswer(i->{
                Set<AccountCharacterData> arg = i.getArgument(0);
                PlayerCharacter character = arg.iterator().next().getCharacter();
                character.setId(1L);
                return Set.of(character);
            });

        Instant begin = SC2Pulse.instant();
        stubLadderApi();
        //update current season
        privacyService.updateOldSeasons();
        verify(playerCharacterDAO).updateAccountsAndCharacters(accountPlayerCaptor.capture());
        //true because season is the current season and alternative update route is disabled
        assertTrue(accountPlayerCaptor.getValue().iterator().next().isFresh());

        verify(clanService).saveClans(clanDataCaptor.capture());
        Assertions.assertThat(clanDataCaptor.getValue())
            .usingRecursiveComparison()
            .withEqualsForType((l, r)->l.compareTo(r) >= 0, Instant.class)
            .isEqualTo(!charUpdated ? List.of() : List.of(
                new ClanMemberEventData
                (
                    new PlayerCharacter(1L,  null, Region.EU, 1L, 1, "name2#1"),
                    new Clan(null, "tag", Region.EU, "clanName"),
                    begin
                )
            ));
    }

    @Test
    public void whenAlreadyUpdatingOldData_thenDoNothing()
    {
        when(seasonDAO.getMaxBattlenetId()).thenReturn(BlizzardSC2API.FIRST_SEASON + 1);
        when(statsService.isAlternativeUpdate(any(), anyBoolean())).thenReturn(false);
        when(sc2WebServiceUtil.getExternalOrExistingSeason(any(), anyInt())).thenReturn(new BlizzardSeason());

        Duration delay = Duration.ofMillis(100);
        OngoingStubbing<Flux<Tuple2<BlizzardLadder, Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>>>>
            stub = stubLadderApi(delay).thenReturn(createLadder().delayElements(delay));
        for(int i = 0; i < globalContext.getActiveRegions().size(); i++)
            stub = stub.thenReturn(Flux.empty());
        List<Future<Void>> tasks = new ArrayList<>(2);
        doAnswer(i->{
            Runnable r = i.getArgument(0);
            Future<Void> task = TestUtil.EXECUTOR_SERVICE.submit(r, null);
            tasks.add(task);
            return task;
        }).when(executor).execute(any(Runnable.class));
        //update current season
        privacyService.updateOldSeasons();
        //second update is ignored
        privacyService.updateOldSeasons();
        MiscUtil.awaitAndThrowException(tasks, false, false);
        //executed once
        verify(playerCharacterDAO, times(1)).updateAccountsAndCharacters(accountPlayerCaptor.capture());
        //true because season is the current season and alternative update route is disabled
        assertTrue(accountPlayerCaptor.getValue().iterator().next().isFresh());
    }

    private OngoingStubbing<Flux<Tuple2<BlizzardLadder, Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>>>> stubLadderApi(Duration delay)
    {
        OngoingStubbing<Flux<Tuple2<BlizzardLadder, Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>>>> stub
            = when(api.getLadders(any(), eq(-1L), argThat(Map::isEmpty), eq(REQUEST_LIMIT_PRIORITY_NAME)))
                .thenReturn(createLadder().delayElements(delay));
        for(int i = 0; i < globalContext.getActiveRegions().size(); i++)
            stub = stub.thenReturn(Flux.empty());
        return stub;
    }

    private OngoingStubbing<Flux<Tuple2<BlizzardLadder, Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>>>> stubLadderApi()
    {
        return stubLadderApi(Duration.ZERO);
    }

    private Flux<Tuple2<BlizzardLadder, Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>>>
    createLadder()
    {
        BlizzardTeamMember member = new BlizzardTeamMember
        (
            new BlizzardPlayerCharacter(1L, 1, "name2#1"), new BlizzardTeamMemberRace[0],
            new BlizzardAccount(1L, "btag1")
        );
        member.setClan(new BlizzardClan(1L, "tag", "clanName"));
        return Flux.just
        (
            Tuples.of
            (
                new BlizzardLadder
                (
                    new BlizzardTeam[]
                    {
                        new BlizzardTeam
                        (
                            BigInteger.ONE,
                            new BlizzardTeamMember[]{member},
                            SC2Pulse.instant(), 1L, 1, 1, 1, 1
                        )
                    },
                    new BlizzardLadderLeague()
                ),
                Tuples.of(new BlizzardLeague(), Region.EU, new BlizzardLeagueTier(), new BlizzardTierDivision())
            )
        );
    }

}
