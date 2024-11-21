// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.nephest.battlenet.sc2.web.service.BlizzardSC2API;
import com.nephest.battlenet.sc2.web.service.SC2ArcadeAPI;
import org.springframework.boot.test.mock.mockito.SpyBean;

public class SpyBeanConfig
{

    @SpyBean
    private SC2ArcadeAPI arcadeAPI;

    @SpyBean
    private BlizzardSC2API api;

}
