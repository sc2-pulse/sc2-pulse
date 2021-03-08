// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
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

@Profile({"!maintenance & !dev"})
@Component
public class Cron
{

    private static final Logger LOG = LoggerFactory.getLogger(Cron.class);

    private static final BaseLeague.LeagueType[] NORMAL_LEAGUES = BaseLeague.LeagueType.values();
    private static final BaseLeague.LeagueType[] ALTERNATIVE_LEAGUES = new BaseLeague.LeagueType[]
    {
        BaseLeague.LeagueType.GRANDMASTER,
        BaseLeague.LeagueType.MASTER,
        BaseLeague.LeagueType.DIAMOND
    };

    @Autowired
    private StatsService statsService;

    @Autowired
    private ProPlayerService proPlayerService;

    @Autowired
    private MatchService matchService;

    @Autowired
    private PostgreSQLUtils postgreSQLUtils;

    @Scheduled(cron="0 0 7 * * *")
    public void updateSeasons()
    {
        doUpdateSeasons();
    }

    @Scheduled(cron="0 */15 0-5,8-23 * * MON-TUE,THU-SUN")
    public void updateTop()
    {
        doUpdateTop();
    }

    @Scheduled(cron="0 0,15,30 6 * * *")
    public void updateTopPreDiscovery()
    {
        doUpdateTop();
    }

    @Scheduled(cron="0 */15 0-5,8-11,13-23 * * WED")
    public void updateTopWed()
    {
        doUpdateTop();
    }

    @Scheduled(cron="0 0,15 12 * * WED")
    public void updateTopPreMaintenance()
    {
        doUpdateTop();
    }

    private void doUpdateSeasons()
    {
        try
        {
            statsService.updateCurrent
            (
                Region.values(),
                QueueType.getTypes(StatsService.VERSION).toArray(QueueType[]::new),
                BaseLeague.LeagueType.values()
            );
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
        }
    }

    private void doUpdateTop()
    {
        try
        {
            statsService.updateCurrent
            (
                new Region[]{Region.US, Region.EU, Region.KR},
                new QueueType[]{QueueType.LOTV_1V1},
                statsService.getAlternativeRegions().contains(Region.EU)
                    || statsService.getAlternativeRegions().contains(Region.US)
                        ? ALTERNATIVE_LEAGUES
                        : NORMAL_LEAGUES
            );
            matchService.update();
        }
        catch(RuntimeException ex)
        {
            //API can be broken randomly. All we can do at this point is log the exception.
            LOG.error(ex.getMessage(), ex);
        }
    }

}
