// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.validation.Validator;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    private SC2WebServiceUtil sc2WebServiceUtil;

    @Mock
    private ExecutorService executorService;

    public BlizzardPrivacyService privacyService;

    private AutoCloseable mocks;

    @BeforeEach
    public void beforeEach()
    {
        mocks = MockitoAnnotations.openMocks(this);
        when(executorService.submit(any(Runnable.class))).thenReturn(mock(Future.class));
        privacyService = new BlizzardPrivacyService
        (
            api,
            statsService,
            alternativeLadderService,
            seasonDAO,
            varDAO,
            accountDAO,
            playerCharacterDAO,
            executorService,
            executorService,
            validator,
            sc2WebServiceUtil
        );
    }

    @AfterEach
    public void afterEach()
    throws Exception
    {
        mocks.close();
    }

    @Test
    public void testGetSeasonToUpdate()
    {
        //there ate no seasons in the db, nothing to update
        assertNull(privacyService.getSeasonToUpdate());

        //there are seasons in the db, current season update is prioritized
        when(seasonDAO.getMaxBattlenetId()).thenReturn(BlizzardSC2API.FIRST_SEASON + 2);
        assertEquals(BlizzardSC2API.FIRST_SEASON + 2, privacyService.getSeasonToUpdate());

        //season was updated recently, nothing to update
        privacyService.update();
        assertNull(privacyService.getSeasonToUpdate());

        OffsetDateTime anonymizeOffset = OffsetDateTime.of(2015, 1, 1, 0, 0, 0, 0, OffsetDateTime.now().getOffset());
        InOrder order = inOrder(accountDAO, playerCharacterDAO);
        order.verify(accountDAO).removeEmptyAccounts();
        order.verify(accountDAO).anonymizeExpiredAccounts(argThat(m->m.isEqual(anonymizeOffset)));
        order.verify(playerCharacterDAO).anonymizeExpiredCharacters(argThat(m->m.isEqual(anonymizeOffset)));

        long updateTimeFrame = BlizzardPrivacyService.DATA_TTL
            .dividedBy(BlizzardPrivacyService.CURRENT_SEASON_UPDATES_PER_PERIOD + 2) //+ 2 existing seasons
            .toSeconds();

        long currentUpdateTimeFrame = BlizzardPrivacyService.DATA_TTL
            .dividedBy(BlizzardPrivacyService.CURRENT_SEASON_UPDATES_PER_PERIOD)
            .toSeconds();

        //rewind update timestamp to simulate the time flow
        privacyService.getLastUpdatedSeasonInstantVar().setValue(Instant.now().minusSeconds(updateTimeFrame));
        assertEquals(BlizzardSC2API.FIRST_SEASON, privacyService.getSeasonToUpdate());
        privacyService.update();

        //rewind update timestamp to simulate the time flow, next season is updated
        privacyService.getLastUpdatedSeasonInstantVar().setValue(Instant.now().minusSeconds(updateTimeFrame));
        assertEquals(BlizzardSC2API.FIRST_SEASON + 1, privacyService.getSeasonToUpdate());
        privacyService.update();

        //rewind update timestamp to simulate the time flow, all previous season were updated, starting from the first
        //season again
        privacyService.getLastUpdatedSeasonInstantVar().setValue(Instant.now().minusSeconds(updateTimeFrame));
        assertEquals(BlizzardSC2API.FIRST_SEASON, privacyService.getSeasonToUpdate());
        privacyService.update();

        //current season update is prioritized
        privacyService.getLastUpdatedSeasonInstantVar().setValue(Instant.now().minusSeconds(updateTimeFrame));
        privacyService.getLastUpdatedCurrentSeasonInstantVar().setValue(Instant.now().minusSeconds(currentUpdateTimeFrame));
        assertEquals(BlizzardSC2API.FIRST_SEASON + 2, privacyService.getSeasonToUpdate());
        privacyService.update();
    }
    
}
