// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.BaseLeague.LeagueType;
import com.nephest.battlenet.sc2.model.BaseLeagueTier.LeagueTierType;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterStats;
import com.nephest.battlenet.sc2.model.local.QueueStats;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterStatsDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.MergedLadderSearchStatsResult;
import com.nephest.battlenet.sc2.model.local.ladder.PagedSearchResult;
import com.nephest.battlenet.sc2.model.local.ladder.common.CommonCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.dao.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class LadderController
{

    @Autowired
    private LadderSearchDAO ladderSearch;

    @Autowired
    private LadderCharacterDAO ladderCharacterDAO;

    @Autowired
    private LadderStatsDAO ladderStatsDAO;

    @Autowired
    private PlayerCharacterStatsDAO playerCharacterStatsDAO;

    @Autowired
    private LadderProPlayerDAO ladderProPlayerDAO;

    @Autowired
    private LadderMatchDAO ladderMatchDAO;

    @GetMapping("/ladder")
    public PagedSearchResult<List<LadderTeam>> getLadder
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
        @RequestParam(name = "gra", required = false) boolean grandmaster,
        @RequestParam(name = "page", defaultValue="1") int page
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
        return ladderSearch.find
        (
            season,
            regions,
            leagues,
            queue,
            teamType,
            page
        );
    }

    @GetMapping("/ladder/a/{ratingAnchor}/{idAnchor}/{forward}/{count}")
    public PagedSearchResult<List<LadderTeam>> getLadderAnchored
        (
            @PathVariable("ratingAnchor") long ratingAnchor,
            @PathVariable("idAnchor") long idAnchor,
            @PathVariable("forward") boolean forward,
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
        return ladderSearch.findAnchored
        (
            season,
            regions,
            leagues,
            queue,
            teamType,
            page,
            ratingAnchor,
            idAnchor,
            forward,
            count
        );
    }

    @GetMapping("/ladder/stats/queue/{queueType}/{teamType}")
    public List<QueueStats> getQueueStats
    (
        @PathVariable("queueType") QueueType queueType,
        @PathVariable("teamType") TeamType teamType
    )
    {
        return ladderStatsDAO.findQueueStats(queueType, teamType);
    }

    @GetMapping("/ladder/stats")
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

    @GetMapping("/ladder/stats/bundle")
    public Map<QueueType, Map<TeamType, Map<Integer, MergedLadderSearchStatsResult>>> getLadderStatsBundle()
    {
        return ladderStatsDAO.findStats();
    }

    @GetMapping("/ladder/league/bounds")
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

    @GetMapping("/seasons")
    public List<Season> getSeasons()
    {
        return ladderSearch.findSeasonList();
    }

    @GetMapping("/character/{id}/common")
    public CommonCharacter getCommonCharacter
    (
        @PathVariable("id") long id
    )
    {
        return new CommonCharacter
        (
            ladderSearch.findCharacterTeams(id),
            ladderCharacterDAO.findLinkedDistinctCharactersByCharacterId(id),
            playerCharacterStatsDAO.findGlobalList(id),
            ladderProPlayerDAO.getProPlayerByCharacterId(id),
            ladderMatchDAO.findMatchesByCharacterId(id)
        );
    }

    @GetMapping("/character/{id}/teams")
    public List<LadderTeam> getCharacterTeams
    (
        @PathVariable("id") long id
    )
    {
        return ladderSearch.findCharacterTeams(id);
    }

    @GetMapping("/character/{id}/stats")
    public List<PlayerCharacterStats> getCharacterStats
    (
        @PathVariable("id") long id
    )
    {
        return playerCharacterStatsDAO.findGlobalList(id);
    }

    @GetMapping("/characters")
    public List<LadderDistinctCharacter> getCharacterTeams
    (
        @RequestParam("name") String name
    )
    {
        return ladderCharacterDAO.findDistinctCharactersByName(name);
    }
}
