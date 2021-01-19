// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import com.nephest.battlenet.sc2.web.service.MatchService;
import com.nephest.battlenet.sc2.web.service.ProPlayerService;
import com.nephest.battlenet.sc2.web.service.StatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Profile("!maintenance")
@Component
public class Cron
{

    private static final Logger LOG = LoggerFactory.getLogger(Cron.class);

    @Autowired
    private StatsService statsService;

    @Autowired
    private ProPlayerService proPlayerService;

    @Autowired
    private MatchService matchService;

    @Autowired
    private LadderSearchDAO ladderSearchDAO;

    @Autowired
    private PostgreSQLUtils postgreSQLUtils;

    @Scheduled(cron="0 0 3 * * *")
    public void updateSeasons()
    {
        try
        {
            statsService.updateCurrent(Region.values(), QueueType.values(), BaseLeague.LeagueType.values());
            proPlayerService.update();
            matchService.update();
        }
        catch(RuntimeException ex)
        {
            //API can be broken randomly. All we can do at this point is log the exception.
            LOG.error(ex.getMessage(), ex);
        }
        finally
        {
            postgreSQLUtils.vacuum();
            postgreSQLUtils.analyze();
            ladderSearchDAO.precache();
        }
    }

    @Scheduled(cron="0 */30 0-1,4-11,13-23 * * *")
    public void updateTop()
    {
        try
        {
            statsService.updateCurrent
            (
                new Region[]{Region.US, Region.EU, Region.KR},
                new QueueType[]{QueueType.LOTV_1V1},
                new BaseLeague.LeagueType[]
                {
                    BaseLeague.LeagueType.GRANDMASTER,
                    BaseLeague.LeagueType.MASTER,
                    BaseLeague.LeagueType.DIAMOND
                }
            );
        }
        catch(RuntimeException ex)
        {
            //API can be broken randomly. All we can do at this point is log the exception.
            LOG.error(ex.getMessage(), ex);
        }
        finally
        {
            ladderSearchDAO.precache();
        }
    }

}
