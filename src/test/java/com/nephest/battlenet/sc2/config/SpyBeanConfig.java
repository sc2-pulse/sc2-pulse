// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.nephest.battlenet.sc2.model.local.dao.DonationRepository;
import com.nephest.battlenet.sc2.model.local.dao.PatreonDonationStateRepository;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.web.service.BlizzardSC2API;
import com.nephest.battlenet.sc2.web.service.MonetaryAmountConverter;
import com.nephest.battlenet.sc2.web.service.SC2ArcadeAPI;
import org.springframework.boot.test.mock.mockito.SpyBean;

public class SpyBeanConfig
{

    @SpyBean
    private SeasonDAO seasonDAO;

    @SpyBean
    private SC2ArcadeAPI arcadeAPI;

    @SpyBean
    private BlizzardSC2API api;

    @SpyBean
    private VarDAO varDAO;

    @SpyBean
    private TeamStateDAO teamStateDAO;

    @SpyBean
    private DonationRepository donationRepository;

    @SpyBean
    private PatreonDonationStateRepository patreonDonationStateRepository;

    @SpyBean
    private MonetaryAmountConverter monetaryAmountConverter;

}
