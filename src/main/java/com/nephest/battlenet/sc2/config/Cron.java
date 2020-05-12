// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.web.service.StatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Cron
{

    @Autowired
    private StatsService statsService;

    @Autowired
    private AccountDAO accountDao;

    @Scheduled(cron="0 0 3 * * *")
    public void updateCurrentSeason()
    {
        statsService.updateCurrent();
    }

    @Scheduled(cron="0 0 5 * * SAT")
    public void updateAllSeasons()
    {
        statsService.updateAll();
    }

    @Scheduled(cron="45 6 0 * * *")
    public void removeExpiredPrivacy()
    {
        accountDao.removeExpiredByPrivacy();
    }

}
