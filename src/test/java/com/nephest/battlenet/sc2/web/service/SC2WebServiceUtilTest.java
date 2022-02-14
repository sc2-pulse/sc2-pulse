// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardSeason;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SC2WebServiceUtilTest
{

    private BlizzardSC2API api;
    private SeasonDAO seasonDAO;
    private SC2WebServiceUtil sc2WebServiceUtil;

    @BeforeEach
    public void beforeEach()
    {
        api = mock(BlizzardSC2API.class);
        seasonDAO = mock(SeasonDAO.class);
        sc2WebServiceUtil = new SC2WebServiceUtil(api, seasonDAO);
    }

    @Test
    public void getExternalOrExistingSeason()
    {
        BlizzardSeason apiSeason = new BlizzardSeason();
        Season dbSeason = new Season(1, 1, Region.EU, 4, 5, LocalDate.now(), LocalDate.now());
        when(api.getSeason(Region.EU, 1))
            .thenReturn(Mono.just(apiSeason))
            .thenThrow(new WebClientResponseException(0, "", null, null, null))
            .thenThrow(new WebClientResponseException(0, "", null, null, null));
        when(seasonDAO.findListByRegion(Region.EU))
            .thenReturn(List.of(dbSeason))
            .thenReturn(List.of());

        //the first call returns external season
        assertSame(apiSeason, sc2WebServiceUtil.getExternalOrExistingSeason(Region.EU, 1));
        //the second call throws an exception, returning the existing season
        assertEquals(dbSeason, Season.of(sc2WebServiceUtil.getExternalOrExistingSeason(Region.EU, 1), Region.EU));
        //the third call throws an exception and there is no existing season
        assertThrows(NoSuchElementException.class, ()->sc2WebServiceUtil.getExternalOrExistingSeason(Region.EU, 1));
    }

}
