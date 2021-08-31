// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterStats;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterStatsDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMatch;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.PagedSearchResult;
import com.nephest.battlenet.sc2.model.local.ladder.common.CommonCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.dao.*;
import com.nephest.battlenet.sc2.web.service.PlayerCharacterReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/character")
public class CharacterController
{

    @Autowired
    private LadderSearchDAO ladderSearch;

    @Autowired
    private LadderCharacterDAO ladderCharacterDAO;

    @Autowired
    private PlayerCharacterStatsDAO playerCharacterStatsDAO;

    @Autowired
    private LadderPlayerCharacterStatsDAO ladderPlayerCharacterStatsDAO;

    @Autowired
    private LadderProPlayerDAO ladderProPlayerDAO;

    @Autowired
    private LadderMatchDAO ladderMatchDAO;

    @Autowired
    private LadderTeamStateDAO ladderTeamStateDAO;

    @Autowired
    private PlayerCharacterReportService reportService;

    @GetMapping("/{id}/common")
    public CommonCharacter getCommonCharacter
    (
        @PathVariable("id") long id
    )
    {
        return new CommonCharacter
        (
            ladderSearch.findCharacterTeams(id),
            ladderCharacterDAO.findLinkedDistinctCharactersByCharacterId(id),
            ladderPlayerCharacterStatsDAO.findGlobalList(id),
            ladderProPlayerDAO.getProPlayerByCharacterId(id),
            ladderMatchDAO.findMatchesByCharacterId(
                id, OffsetDateTime.now(), BaseMatch.MatchType._1V1, "map", 0, 1).getResult(),
            ladderTeamStateDAO.find(id),
            reportService.findReportsByCharacterId(id)
        );
    }

    @GetMapping("/{id}/matches/{dateAnchor}/{typeAnchor}/{mapAnchor}/{page}/{pageDiff}")
    public PagedSearchResult<List<LadderMatch>> getCharacterMatches
    (
        @PathVariable("id") long id,
        @PathVariable("dateAnchor") String dateAnchor,
        @PathVariable("typeAnchor") BaseMatch.MatchType typeAnchor,
        @PathVariable("mapAnchor") String mapAnchor,
        @PathVariable("page") int page,
        @PathVariable("pageDiff") int pageDiff
    )
    {
        return ladderMatchDAO.findMatchesByCharacterId
        (
            id,
            OffsetDateTime.parse(dateAnchor),
            typeAnchor,
            mapAnchor,
            page,
            pageDiff
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

}
