// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.nephest.battlenet.sc2.web.service.StatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Cron
{

    @Autowired
    private StatsService statsService;

    @Scheduled(cron="0 0 3 * * *")
    public void updateCurrentSeason()
    {
        statsService.updateCurrent();
    }

    @Scheduled(cron="0 0 5 * * SAT")
    public void updateMissingSeasons()
    {
        statsService.updateMissing();
    }

}
