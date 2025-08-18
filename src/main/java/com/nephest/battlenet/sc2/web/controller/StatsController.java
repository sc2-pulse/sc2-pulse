// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.QueueStats;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMapStatsFilm;
import com.nephest.battlenet.sc2.model.local.ladder.MergedLadderSearchStatsResult;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderStatsDAO;
import com.nephest.battlenet.sc2.web.service.MapService;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController
{

    @Autowired
    private LadderStatsDAO ladderStatsDAO;

    @Autowired
    private MapService mapService;

    @GetMapping("/player-base")
    public List<QueueStats> getPlayerBaseStats
    (
        @RequestParam("queueType") QueueType queueType,
        @RequestParam("teamType") TeamType teamType
    )
    {
        return ladderStatsDAO.findQueueStats(queueType, teamType);
    }

    @GetMapping("/activity")
    public Map<Integer, MergedLadderSearchStatsResult> getActivityStats
    (
        @RequestParam("queue") QueueType queue,
        @RequestParam("team-type") TeamType teamType,
        @RequestParam(value = "region", defaultValue = "") Set<Region> regions,
        @RequestParam(value = "league", defaultValue = "") Set<BaseLeague.LeagueType> leagues
    )
    {
        return ladderStatsDAO.findStats(regions, leagues, queue, teamType);
    }

    @GetMapping("/balance-reports")
    public ResponseEntity<LadderMapStatsFilm> getBalanceReports
    (
        @RequestParam("season") int season,
        @RequestParam(value = "region", defaultValue = "") Set<Region> regions,
        @RequestParam("queue") QueueType queue,
        @RequestParam("teamType") TeamType teamType,
        @RequestParam("league") BaseLeague.LeagueType league,
        @RequestParam("tier") BaseLeagueTier.LeagueTierType tier,
        @RequestParam(value = "crossTier", defaultValue = "") Set<Boolean> crossTier,
        @RequestParam(value = "frameNumberMax", required = false) Integer frameNumberMax,
        @RequestParam(value = "race", defaultValue = "") Set<Race> races
    )
    {
        if(regions.isEmpty()) regions = EnumSet.allOf(Region.class);
        if(races.isEmpty()) races = EnumSet.allOf(Race.class);

        return WebServiceUtil.notFoundIfNull(mapService.findFilm(
            races,
            MapService.FILM_FRAME_DURATION,
            frameNumberMax,
            season,
            regions,
            queue,
            teamType,
            league,
            tier,
            crossTier
        ));
    }

}
