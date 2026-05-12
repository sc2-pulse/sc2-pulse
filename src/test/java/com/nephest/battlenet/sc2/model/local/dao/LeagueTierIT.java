// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.extension.AutoConfigureDatabase;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.local.LeagueTier;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@AutoConfigureDatabase
public class LeagueTierIT
{

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private LeagueTierDAO leagueTierDAO;

    @Test
    public void testMergeNullTiers()
    {
        seasonGenerator.generateDefaultSeason(0);
        LeagueTier nullTier1 = leagueTierDAO.merge(new LeagueTier(null, 1, null, null, null));
        LeagueTier nullTier2 = leagueTierDAO.merge(new LeagueTier(null, 1, null, null, null));
        assertNotNull(nullTier1.getId());
        assertEquals(nullTier1.getId(), nullTier2.getId());

        LeagueTier nullTier3 = leagueTierDAO
            .merge(new LeagueTier(null, 1, BaseLeagueTier.LeagueTierType.SECOND, null, null));
        assertNotNull(nullTier3.getId());
    }

}
