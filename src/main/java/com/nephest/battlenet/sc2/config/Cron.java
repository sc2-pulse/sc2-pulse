// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import com.nephest.battlenet.sc2.web.service.ProPlayerService;
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
    private ProPlayerService proPlayerService;

    @Autowired
    private LadderSearchDAO ladderSearchDAO;

    @Autowired
    private PostgreSQLUtils postgreSQLUtils;

    @Scheduled(cron="0 0 3 * * *")
    public void updateSeasons()
    {
        statsService.updateMissing();
        statsService.updateCurrent();
        proPlayerService.update();
        postgreSQLUtils.vacuum();
        postgreSQLUtils.analyze();
        ladderSearchDAO.precache();
    }

}
