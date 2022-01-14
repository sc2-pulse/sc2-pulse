// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterStats;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterStatsDAO;
import com.nephest.battlenet.sc2.model.local.inner.PlayerCharacterSummary;
import com.nephest.battlenet.sc2.model.local.inner.PlayerCharacterSummaryDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMatch;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.PagedSearchResult;
import com.nephest.battlenet.sc2.model.local.ladder.common.CommonCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.dao.*;
import com.nephest.battlenet.sc2.web.service.PlayerCharacterReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/character")
public class CharacterController
{

    public static final int SUMMARY_DEPTH_MAX = 120;
    public static final int SUMMARY_IDS_MAX = 50;

    @Autowired
    private LadderSearchDAO ladderSearch;

    @Autowired
    private LadderCharacterDAO ladderCharacterDAO;

    @Autowired
    private PlayerCharacterStatsDAO playerCharacterStatsDAO;

    @Autowired
    private LadderPlayerCharacterStatsDAO ladderPlayerCharacterStatsDAO;

    @Autowired
    private PlayerCharacterSummaryDAO playerCharacterSummaryDAO;

    @Autowired
    private LadderProPlayerDAO ladderProPlayerDAO;

    @Autowired
    private LadderMatchDAO ladderMatchDAO;

    @Autowired
    private LadderTeamStateDAO ladderTeamStateDAO;

    @Autowired
    private PlayerCharacterReportService reportService;

    @GetMapping
    ({
        "/{id}/common",
        "/{id}/common/{types}"
    })
    public CommonCharacter getCommonCharacter
    (
        @PathVariable("id") long id,
        @PathVariable(name = "types", required = false) BaseMatch.MatchType[] types
    )
    {
        if(types == null) types = new BaseMatch.MatchType[0];
        return new CommonCharacter
        (
            ladderSearch.findCharacterTeams(id),
            ladderCharacterDAO.findLinkedDistinctCharactersByCharacterId(id),
            ladderPlayerCharacterStatsDAO.findGlobalList(id),
            ladderProPlayerDAO.getProPlayerByCharacterId(id),
            ladderMatchDAO.findMatchesByCharacterId(
                id, OffsetDateTime.now(), BaseMatch.MatchType._1V1, 0, 0, 1, types).getResult(),
            ladderTeamStateDAO.find(id),
            reportService.findReportsByCharacterId(id)
        );
    }

    @GetMapping
    ({
        "/{id}/matches/{dateAnchor}/{typeAnchor}/{mapAnchor}/{page}/{pageDiff}",
        "/{id}/matches/{dateAnchor}/{typeAnchor}/{mapAnchor}/{page}/{pageDiff}/{types}"
    })
    public PagedSearchResult<List<LadderMatch>> getCharacterMatches
    (
        @PathVariable("id") long id,
        @PathVariable("dateAnchor") String dateAnchor,
        @PathVariable("typeAnchor") BaseMatch.MatchType typeAnchor,
        @PathVariable("mapAnchor") int mapAnchor,
        @PathVariable("page") int page,
        @PathVariable("pageDiff") int pageDiff,
        @PathVariable(name = "types", required = false) BaseMatch.MatchType[] types
    )
    {
        if(Math.abs(pageDiff) > 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page count is too big");
        if(types == null) types = new BaseMatch.MatchType[0];

        return ladderMatchDAO.findMatchesByCharacterId
        (
            id,
            OffsetDateTime.parse(dateAnchor),
            typeAnchor,
            mapAnchor,
            page,
            pageDiff,
            types
        );
    }

    @GetMapping("/{id}/teams")
    public List<LadderTeam> getCharacterTeams
    (
        @PathVariable("id") long id
    )
    {
        return ladderSearch.findCharacterTeams(id);
    }

    @GetMapping("/{id}/stats")
    public List<PlayerCharacterStats> getCharacterStats
    (
        @PathVariable("id") long id
    )
    {
        return playerCharacterStatsDAO.findGlobalList(id);
    }

    @GetMapping
    ({
        "/{ids}/summary/1v1/{depthDays}",
        "/{ids}/summary/1v1/{depthDays}/{races}"
    })
    public List<PlayerCharacterSummary> getCharacterSummary
    (
        @PathVariable("ids") Long[] ids,
        @PathVariable("depthDays") int depth,
        @PathVariable(name = "races", required = false) Race[] races
    )
    {
        if(ids.length > SUMMARY_IDS_MAX)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Id list is too long, max: " + SUMMARY_IDS_MAX);
        if(depth > SUMMARY_DEPTH_MAX)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Depth is too big, max: " + SUMMARY_DEPTH_MAX);
        if(races == null) races = Race.EMPTY_RACE_ARRAY;

        return playerCharacterSummaryDAO.find(ids, OffsetDateTime.now().minusDays(depth), races);
    }

}
