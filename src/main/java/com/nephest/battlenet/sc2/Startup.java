// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2;

import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.web.service.StatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

@Profile("!maintenance")
@Component
public class Startup
implements ApplicationRunner
{

    @Autowired
    private LadderSearchDAO ladderSearchDAO;

    @Autowired
    private StatsService statsService;

    @Autowired @Qualifier("taskScheduler")
    private Executor executor;

    @Override
    public void run(ApplicationArguments args)
    {
        executor.execute(()->ladderSearchDAO.precache());
        executor.execute(()->statsService.checkStaleData());
    }
}
