// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.BaseLeague.LeagueType;
import com.nephest.battlenet.sc2.model.BaseLeagueTier.LeagueTierType;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.QueueStats;
import com.nephest.battlenet.sc2.model.local.ladder.LadderLeagueStats;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMapStats;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMapStatsFilm;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.MergedLadderSearchStatsResult;
import com.nephest.battlenet.sc2.model.local.ladder.PagedSearchResult;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderMapStatsDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderStatsDAO;
import com.nephest.battlenet.sc2.web.service.MapService;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ladder")
public class LadderController
{

    public static final int PAGE_COUNT_MAX = 15;

    @Autowired
    private LadderSearchDAO ladderSearch;

    @Autowired
    private LadderStatsDAO ladderStatsDAO;

    @Autowired
    private LadderMapStatsDAO ladderMapStatsDAO;

    @Autowired
    private MapService mapService;

    @GetMapping("/a/{ratingCursor}/{idCursor}/{count}")
    public PagedSearchResult<List<LadderTeam>> getLadder
        (
            @PathVariable("ratingCursor") long ratingCursor,
            @PathVariable("idCursor") long idCursor,
            @PathVariable("count") int count,
            @RequestParam("season") int season,
            @RequestParam("queue") QueueType queue,
            @RequestParam("team-type") TeamType teamType,
            @RequestParam(name = "page") int page,
            @RequestParam(name = "us", required = false) boolean us,
            @RequestParam(name = "eu", required = false) boolean eu,
            @RequestParam(name = "kr", required = false) boolean kr,
            @RequestParam(name = "cn", required = false) boolean cn,
            @RequestParam(name = "bro", required = false) boolean bronze,
            @RequestParam(name = "sil", required = false) boolean silver,
            @RequestParam(name = "gol", required = false) boolean gold,
            @RequestParam(name = "pla", required = false) boolean platinum,
            @RequestParam(name = "dia", required = false) boolean diamond,
            @RequestParam(name = "mas", required = false) boolean master,
            @RequestParam(name = "gra", required = false) boolean grandmaster
        )
    {
        if(Math.abs(count) > PAGE_COUNT_MAX)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page count is too big");

        Set<Region> regions = EnumSet.noneOf(Region.class);
        if(us) regions.add(Region.US);
        if(eu) regions.add(Region.EU);
        if(kr) regions.add(Region.KR);
        if(cn) regions.add(Region.CN);

        Set<LeagueType> leagues = EnumSet.noneOf(LeagueType.class);
        if(bronze) leagues.add(LeagueType.BRONZE);
        if(silver) leagues.add(LeagueType.SILVER);
        if(gold) leagues.add(LeagueType.GOLD);
        if(platinum) leagues.add(LeagueType.PLATINUM);
        if(diamond) leagues.add(LeagueType.DIAMOND);
        if(master) leagues.add(LeagueType.MASTER);
        if(grandmaster) leagues.add(LeagueType.GRANDMASTER);
        return ladderSearch.find
        (
            season,
            regions,
            leagues,
            queue,
            teamType,
            page,
            ratingCursor,
            idCursor,
            count
        );
    }

    @GetMapping("/stats/queue/{queueType}/{teamType}")
    public List<QueueStats> getQueueStats
    (
        @PathVariable("queueType") QueueType queueType,
        @PathVariable("teamType") TeamType teamType
    )
    {
        return ladderStatsDAO.findQueueStats(queueType, teamType);
    }

    @GetMapping("/stats")
    public Map<Integer, MergedLadderSearchStatsResult> getLadderStats
    (
        @RequestParam("queue") QueueType queue,
        @RequestParam("team-type") TeamType teamType,
        @RequestParam(name = "us", required = false) boolean us,
        @RequestParam(name = "eu", required = false) boolean eu,
        @RequestParam(name = "kr", required = false) boolean kr,
        @RequestParam(name = "cn", required = false) boolean cn,
        @RequestParam(name = "bro", required = false) boolean bronze,
        @RequestParam(name = "sil", required = false) boolean silver,
        @RequestParam(name = "gol", required = false) boolean gold,
        @RequestParam(name = "pla", required = false) boolean platinum,
        @RequestParam(name = "dia", required = false) boolean diamond,
        @RequestParam(name = "mas", required = false) boolean master,
        @RequestParam(name = "gra", required = false) boolean grandmaster
    )
    {
        Set<Region> regions = EnumSet.noneOf(Region.class);
        if(us) regions.add(Region.US);
        if(eu) regions.add(Region.EU);
        if(kr) regions.add(Region.KR);
        if(cn) regions.add(Region.CN);

        Set<LeagueType> leagues = EnumSet.noneOf(LeagueType.class);
        if(bronze) leagues.add(LeagueType.BRONZE);
        if(silver) leagues.add(LeagueType.SILVER);
        if(gold) leagues.add(LeagueType.GOLD);
        if(platinum) leagues.add(LeagueType.PLATINUM);
        if(diamond) leagues.add(LeagueType.DIAMOND);
        if(master) leagues.add(LeagueType.MASTER);
        if(grandmaster) leagues.add(LeagueType.GRANDMASTER);
        return ladderStatsDAO.findStats
        (
            regions,
            leagues,
            queue,
            teamType
        );
    }

    @GetMapping("/stats/bundle")
    public Map<QueueType, Map<TeamType, Map<Integer, MergedLadderSearchStatsResult>>> getLadderStatsBundle()
    {
        return ladderStatsDAO.findStats();
    }

    @GetMapping("/stats/league/{season}/{queueType}/{teamType}/{regions}/{leagues}")
    public List<LadderLeagueStats> getLadderLeagueStats
    (
        @PathVariable("season") Integer season,
        @PathVariable("queueType") QueueType queue,
        @PathVariable("teamType") TeamType teamType,
        @PathVariable("regions") Region[] regions,
        @PathVariable("leagues") LeagueType[] leagues
    )
    {
        return ladderStatsDAO.findLeagueStats(season, List.of(regions), List.of(leagues), queue, teamType);
    }

    @GetMapping
    ({
        "/stats/map/{season}/{queueType}/{teamType}/{regions}/{leagues}",
        "/stats/map/{season}/{queueType}/{teamType}/{regions}/{leagues}/{mapId}"
    })
    public LadderMapStats getLadderMapStats
    (
        @PathVariable("season") Integer season,
        @PathVariable("queueType") QueueType queue,
        @PathVariable("teamType") TeamType teamType,
        @PathVariable("regions") Region[] regions,
        @PathVariable("leagues") LeagueType[] leagues,
        @PathVariable(name = "mapId", required = false) Integer mapId
    )
    {
        return ladderMapStatsDAO.find(season, List.of(regions), List.of(leagues), queue, teamType, mapId);
    }

    @GetMapping("/stats/map/film")
    public ResponseEntity<LadderMapStatsFilm> getLadderMapStatsFilm
    (
        @RequestParam("season") int season,
        @RequestParam(value = "region", defaultValue = "") Set<Region> regions,
        @RequestParam("queue") QueueType queue,
        @RequestParam("teamType") TeamType teamType,
        @RequestParam("league") LeagueType league,
        @RequestParam("tier") LeagueTierType tier,
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

    @GetMapping("/league/bounds")
    public Map<Region, Map<LeagueType, Map<LeagueTierType, Integer[]>>> getLadderLeagueBounds
    (
        @RequestParam("season") int season,
        @RequestParam("queue") QueueType queue,
        @RequestParam("team-type") TeamType teamType,
        @RequestParam(name = "us", required = false) boolean us,
        @RequestParam(name = "eu", required = false) boolean eu,
        @RequestParam(name = "kr", required = false) boolean kr,
        @RequestParam(name = "cn", required = false) boolean cn,
        @RequestParam(name = "bro", required = false) boolean bronze,
        @RequestParam(name = "sil", required = false) boolean silver,
        @RequestParam(name = "gol", required = false) boolean gold,
        @RequestParam(name = "pla", required = false) boolean platinum,
        @RequestParam(name = "dia", required = false) boolean diamond,
        @RequestParam(name = "mas", required = false) boolean master,
        @RequestParam(name = "gra", required = false) boolean grandmaster
    )
    {
        Set<Region> regions = EnumSet.noneOf(Region.class);
        if(us) regions.add(Region.US);
        if(eu) regions.add(Region.EU);
        if(kr) regions.add(Region.KR);
        if(cn) regions.add(Region.CN);

        Set<LeagueType> leagues = EnumSet.noneOf(LeagueType.class);
        if(bronze) leagues.add(LeagueType.BRONZE);
        if(silver) leagues.add(LeagueType.SILVER);
        if(gold) leagues.add(LeagueType.GOLD);
        if(platinum) leagues.add(LeagueType.PLATINUM);
        if(diamond) leagues.add(LeagueType.DIAMOND);
        if(master) leagues.add(LeagueType.MASTER);
        if(grandmaster) leagues.add(LeagueType.GRANDMASTER);
        return ladderStatsDAO.findLeagueBounds
        (
            season,
            regions,
            leagues,
            queue,
            teamType
        );
    }

}
