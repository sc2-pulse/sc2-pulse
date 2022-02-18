// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BlizzardSC2APITest
{


    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager;

    @Mock
    private VarDAO varDAO;

    private BlizzardSC2API api;

    private AutoCloseable mocks;

    @BeforeEach
    public void beforeEach()
    {
        mocks = MockitoAnnotations.openMocks(this);
        api = new BlizzardSC2API(objectMapper, oAuth2AuthorizedClientManager, varDAO);
    }

    @AfterEach
    public void afterEach()
    throws Exception
    {
        mocks.close();
    }

    @Test
    public void testRequestCapProgress()
    {
        APIHealthMonitor usMonitor = api.getHealthMonitor(Region.US, false);
        APIHealthMonitor euMonitor = api.getHealthMonitor(Region.EU, false);
        APIHealthMonitor krMonitor = api.getHealthMonitor(Region.KR, false);
        for(int i = 0; i < BlizzardSC2API.REQUESTS_PER_HOUR_CAP / 4; i++) usMonitor.addRequest();
        for(int i = 0; i < BlizzardSC2API.REQUESTS_PER_HOUR_CAP / 3; i++) euMonitor.addRequest();
        for(int i = 0; i < BlizzardSC2API.REQUESTS_PER_HOUR_CAP / 2; i++) krMonitor.addRequest();

        assertEquals(0.25, api.getRequestCapProgress(Region.US));
        assertEquals(0.3333333333333333, api.getRequestCapProgress(Region.EU));
        assertEquals(0.5, api.getRequestCapProgress(Region.KR));
        assertEquals(0.0, api.getRequestCapProgress(Region.CN));
        assertEquals(0.5, api.getRequestCapProgress());
    }

}
