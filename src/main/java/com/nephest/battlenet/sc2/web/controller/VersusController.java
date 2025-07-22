// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.config.openapi.TeamLegacyUids;
import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import com.nephest.battlenet.sc2.model.local.inner.VersusSummary;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMatch;
import com.nephest.battlenet.sc2.model.local.ladder.PagedSearchResult;
import com.nephest.battlenet.sc2.model.local.ladder.Versus;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderMatchDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.web.service.VersusService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/versus")
public class VersusController
{

    public static final int MAX_TEAMS_GROUP1 = 100;
    public static final int MAX_CLANS_GROUP1 = 8;
    public static final int MAX_TEAMS_GROUP2 = 100;
    public static final int MAX_CLANS_GROUP2 = 8;

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
        @RequestParam(name = "team1", defaultValue = "") @TeamLegacyUids Set<TeamLegacyUid> teams1,
        @RequestParam(name = "clan2", defaultValue = "") Integer[] clans2,
        @RequestParam(name = "team2", defaultValue = "") @TeamLegacyUids Set<TeamLegacyUid> teams2,
        @RequestParam(value = "type", defaultValue = "") BaseMatch.MatchType[] types
    )
    {
        checkVersusSize(clans1, teams1, clans2, teams2);
        return versusService.getVersus
        (
            clans1, teams1,
            clans2, teams2,
            OffsetDateTime.MAX, BaseMatch.MatchType._1V1, Integer.MAX_VALUE, Region.US,
            0, 1, types
        );
    }

    @GetMapping("/summary")
    public VersusSummary getVersusSummary
    (
        @RequestParam(name = "clan1", defaultValue = "") Integer[] clans1,
        @RequestParam(name = "team1", defaultValue = "") @TeamLegacyUids Set<TeamLegacyUid> teams1,
        @RequestParam(name = "clan2", defaultValue = "") Integer[] clans2,
        @RequestParam(name = "team2", defaultValue = "") @TeamLegacyUids Set<TeamLegacyUid> teams2,
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

    @GetMapping
    ({
        "/{dateCursor}/{typeCursor}/{mapCursor}/{page}/{pageDiff}/matches",
        "/{dateCursor}/{typeCursor}/{mapCursor}/{regionCursor}/{page}/{pageDiff}/matches",
    })
    public PagedSearchResult<List<LadderMatch>> getVersusMatches
    (
        @PathVariable("dateCursor") String dateCursor,
        @PathVariable("typeCursor") BaseMatch.MatchType typeCursor,
        @PathVariable("mapCursor") int mapCursor,
        @PathVariable(name = "regionCursor", required = false) Region regionCursor,
        @PathVariable("page") int page,
        @PathVariable("pageDiff") int pageDiff,
        @RequestParam(name = "clan1", defaultValue = "") Integer[] clans1,
        @RequestParam(name = "team1", defaultValue = "") @TeamLegacyUids Set<TeamLegacyUid> teams1,
        @RequestParam(name = "clan2", defaultValue = "") Integer[] clans2,
        @RequestParam(name = "team2", defaultValue = "") @TeamLegacyUids Set<TeamLegacyUid> teams2,
        @RequestParam(value = "type", defaultValue = "") BaseMatch.MatchType[] types

    )
    {
        checkVersusSize(clans1, teams1, clans2, teams2);
        return ladderMatchDAO.findVersusMatches
        (
            clans1, teams1,
            clans2, teams2,
            SC2Pulse.offsetDateTime(OffsetDateTime.parse(dateCursor)), typeCursor, mapCursor,
            regionCursor != null ? regionCursor : Region.US,
            page, pageDiff, types
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
