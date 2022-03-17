// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import com.nephest.battlenet.sc2.model.local.inner.VersusSummary;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMatch;
import com.nephest.battlenet.sc2.model.local.ladder.PagedSearchResult;
import com.nephest.battlenet.sc2.model.local.ladder.Versus;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderMatchDAO;
import com.nephest.battlenet.sc2.web.service.VersusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/versus")
public class VersusController
{

    public static final int MAX_TEAMS_GROUP1 = 100;
    public static final int MAX_CLANS_GROUP1 = 6;
    public static final int MAX_TEAMS_GROUP2 = 200;
    public static final int MAX_CLANS_GROUP2 = 12;

    @Autowired
    private LadderMatchDAO ladderMatchDAO;

    @Autowired
    private VersusService versusService;

    @Autowired
    private TeamDAO teamDAO;

    @GetMapping("/common")
    public Versus getVersus
    (
        @RequestParam(name = "clan1", defaultValue = "") Integer[] clans1,
        @RequestParam(name = "team1", defaultValue = "") Set<TeamLegacyUid> teams1,
        @RequestParam(name = "clan2", defaultValue = "") Integer[] clans2,
        @RequestParam(name = "team2", defaultValue = "") Set<TeamLegacyUid> teams2,
        @RequestParam(value = "type", defaultValue = "") BaseMatch.MatchType[] types
    )
    {
        checkVersusSize(clans1, teams1, clans2, teams2);
        return versusService.getVersus
        (
            clans1, teams1,
            clans2, teams2,
            OffsetDateTime.MAX, BaseMatch.MatchType._1V1, Integer.MAX_VALUE,
            0, 1, types
        );
    }

    @GetMapping("/summary")
    public VersusSummary getVersusSummary
    (
        @RequestParam(name = "clan1", defaultValue = "") Integer[] clans1,
        @RequestParam(name = "team1", defaultValue = "") Set<TeamLegacyUid> teams1,
        @RequestParam(name = "clan2", defaultValue = "") Integer[] clans2,
        @RequestParam(name = "team2", defaultValue = "") Set<TeamLegacyUid> teams2,
        @RequestParam(value = "type", defaultValue = "") BaseMatch.MatchType[] types
    )
    {
        checkVersusSize(clans1, teams1, clans2, teams2);
        return ladderMatchDAO.getVersusSummary
        (
            clans1, teams1,
            clans2, teams2,
            types
        );
    }

    @GetMapping("/{dateAnchor}/{typeAnchor}/{mapAnchor}/{page}/{pageDiff}/matches")
    public PagedSearchResult<List<LadderMatch>> getVersusMatches
    (
        @PathVariable("dateAnchor") String dateAnchor,
        @PathVariable("typeAnchor") BaseMatch.MatchType typeAnchor,
        @PathVariable("mapAnchor") int mapAnchor,
        @PathVariable("page") int page,
        @PathVariable("pageDiff") int pageDiff,
        @RequestParam(name = "clan1", defaultValue = "") Integer[] clans1,
        @RequestParam(name = "team1", defaultValue = "") Set<TeamLegacyUid> teams1,
        @RequestParam(name = "clan2", defaultValue = "") Integer[] clans2,
        @RequestParam(name = "team2", defaultValue = "") Set<TeamLegacyUid> teams2,
        @RequestParam(value = "type", defaultValue = "") BaseMatch.MatchType[] types

    )
    {
        checkVersusSize(clans1, teams1, clans2, teams2);
        return ladderMatchDAO.findVersusMatches
        (
            clans1, teams1,
            clans2, teams2,
            OffsetDateTime.parse(dateAnchor), typeAnchor, mapAnchor, page, pageDiff, types
        );
    }

    private void checkVersusSize
    (
        Integer[] clans1, Set<TeamLegacyUid> teams1,
        Integer[] clans2, Set<TeamLegacyUid> teams2
    )
    {
        if
        (
            clans1.length > MAX_CLANS_GROUP1
            || teams1.size() > MAX_TEAMS_GROUP1
            || clans2.length > MAX_CLANS_GROUP2
            || teams2.size() > MAX_TEAMS_GROUP2
        ) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many clans and teams");
    }

}
