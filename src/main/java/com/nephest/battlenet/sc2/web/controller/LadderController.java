// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.BaseLeague.LeagueType;
import com.nephest.battlenet.sc2.model.BaseLeagueTier.LeagueTierType;
import com.nephest.battlenet.sc2.model.CursorNavigation;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.SortingOrder;
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
import com.nephest.battlenet.sc2.model.validation.CursorNavigableResult;
import com.nephest.battlenet.sc2.web.service.MapService;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import io.swagger.v3.oas.annotations.Hidden;
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

    @Hidden
    @GetMapping("/a/{ratingCursor}/{idCursor}/{count}")
    public PagedSearchResult<List<LadderTeam>> getLadderLegacy
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

    @Hidden
    @GetMapping("/a")
    public CursorNavigableResult<List<LadderTeam>> getLadderLegacy
    (
        @RequestParam(value = "ratingCursor", required = false) Long ratingCursor,
        @RequestParam(value = "idCursor", required = false) Long idCursor,
        @RequestParam(value = "sortingOrder", defaultValue = "DESC") SortingOrder sortingOrder,
        @RequestParam("season") int season,
        @RequestParam("queue") QueueType queue,
        @RequestParam("team-type") TeamType teamType,
        @RequestParam(value = "region", defaultValue = "") Set<Region> regions,
        @RequestParam(value = "league", defaultValue = "") Set<LeagueType> leagues
    )
    {
        if(ratingCursor == null) ratingCursor = sortingOrder == SortingOrder.DESC
            ? Long.MAX_VALUE
            : Long.MIN_VALUE;
        if(idCursor == null) idCursor = sortingOrder == SortingOrder.DESC
            ? Long.MAX_VALUE
            : Long.MIN_VALUE;

        return new CursorNavigableResult<>(ladderSearch.find(
            season,
            regions,
            leagues,
            queue,
            teamType,
            2,
            ratingCursor,
            idCursor,
            sortingOrder == SortingOrder.DESC ? 1 : -1
        ).getResult(), new CursorNavigation(null, null));
    }

    @Hidden
    @GetMapping("/stats/queue/{queueType}/{teamType}")
    public List<QueueStats> getQueueStatsLegacy
    (
        @PathVariable("queueType") QueueType queueType,
        @PathVariable("teamType") TeamType teamType
    )
    {
        return ladderStatsDAO.findQueueStats(queueType, teamType);
    }

    @Hidden
    @GetMapping("/stats")
    public Map<Integer, MergedLadderSearchStatsResult> getLadderStatsLegacy
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

    @Hidden
    @GetMapping("/stats/v1")
    public Map<Integer, MergedLadderSearchStatsResult> getLadderStatsLegacy
    (
        @RequestParam("queue") QueueType queue,
        @RequestParam("team-type") TeamType teamType,
        @RequestParam(value = "region", defaultValue = "") Set<Region> regions,
        @RequestParam(value = "league", defaultValue = "") Set<LeagueType> leagues
    )
    {
        return ladderStatsDAO.findStats(regions, leagues, queue, teamType);
    }

    @Hidden
    @GetMapping("/stats/bundle")
    public Map<QueueType, Map<TeamType, Map<Integer, MergedLadderSearchStatsResult>>> getLadderStatsBundleLegacy()
    {
        return ladderStatsDAO.findStats();
    }

    /*TODO
        This should be public once the underlying syntax is improved. The endpoint itself is not
        deprecated.
     */
    @Hidden
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

    @Hidden
    @GetMapping
    ({
        "/stats/map/{season}/{queueType}/{teamType}/{regions}/{leagues}",
        "/stats/map/{season}/{queueType}/{teamType}/{regions}/{leagues}/{mapId}"
    })
    public LadderMapStats getLadderMapStatsLegacy
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

    @Hidden
    @GetMapping("/stats/map/film")
    public ResponseEntity<LadderMapStatsFilm> getLadderMapStatsFilmLegacy
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

    @Hidden
    @GetMapping("/league/bounds")
    public Map<Region, Map<LeagueType, Map<LeagueTierType, Integer[]>>> getLadderLeagueBoundsLegacy
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

    @Hidden
    @GetMapping("/league/bounds/v1")
    public Map<Region, Map<LeagueType, Map<LeagueTierType, Integer[]>>> getLadderLeagueBoundsLegacy
    (
            @RequestParam("season") int season,
            @RequestParam("queue") QueueType queue,
            @RequestParam("team-type") TeamType teamType,
            @RequestParam(value = "region", defaultValue = "") Set<Region> regions,
            @RequestParam(value = "league", defaultValue = "") Set<LeagueType> leagues
        )
    {
        return ladderStatsDAO.findLeagueBounds(season, regions, leagues, queue, teamType);
    }

}
