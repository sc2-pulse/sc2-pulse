// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.CursorNavigation;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMatch;
import com.nephest.battlenet.sc2.model.local.ladder.PagedSearchResult;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderMatchDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.model.validation.CursorNavigableResult;
import io.swagger.v3.oas.annotations.Hidden;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/vod")
public class VodController
{

    @Autowired
    private LadderMatchDAO ladderMatchDAO;

    @Hidden
    @GetMapping
    ({
        "/twitch/search/{dateCursor}/{typeCursor}/{mapCursor}/{regionCursor}/{page}/{pageDiff}",
        "/twitch/search/{dateCursor}/{typeCursor}/{mapCursor}/{page}/{pageDiff}",
    })
    public PagedSearchResult<List<LadderMatch>> getCharacterMatchesLegacy
    (
        @PathVariable("dateCursor") String dateCursor,
        @PathVariable("typeCursor") BaseMatch.MatchType typeCursor,
        @PathVariable("mapCursor") int mapCursor,
        @PathVariable(value = "regionCursor", required = false) Region regionCursor,
        @PathVariable("page") int page,
        @PathVariable("pageDiff") int pageDiff,
        @RequestParam(value = "race", required = false) Race race,
        @RequestParam(value = "versusRace", required = false) Race versusRace,
        @RequestParam(value = "minRating", required = false) Integer minRating,
        @RequestParam(value = "maxRating", required = false) Integer maxRating,
        @RequestParam(value = "minDuration", required = false) Integer minDuration,
        @RequestParam(value = "maxDuration", required = false) Integer maxDuration,
        @RequestParam(value = "includeSubOnly", defaultValue = "false") boolean includeSubOnly,
        @RequestParam(value = "map", required = false) Integer map
    )
    {
        if(Math.abs(pageDiff) > 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page count is too big");

        return ladderMatchDAO.findTwitchVods
        (
            race, versusRace,
            minRating, maxRating,
            minDuration, maxDuration,
            includeSubOnly,
            map,
            SC2Pulse.offsetDateTime(OffsetDateTime.parse(dateCursor)),
            typeCursor,
            mapCursor,
            regionCursor == null ? Region.US : regionCursor,
            page,
            pageDiff
        );
    }

    @GetMapping("/twitch/search")
    public CursorNavigableResult<List<LadderMatch>> getTwitchVods
    (
        @RequestParam(value = "dateCursor", required = false) OffsetDateTime dateCursor,
        @RequestParam(value = "typeCursor", defaultValue = "_1V1") BaseMatch.MatchType typeCursor,
        @RequestParam(value = "mapCursor", defaultValue = "0") int mapCursor,
        @RequestParam(value = "regionCursor", defaultValue = "US") Region regionCursor,
        @RequestParam(value = "race", required = false) Race race,
        @RequestParam(value = "versusRace", required = false) Race versusRace,
        @RequestParam(value = "minRating", required = false) Integer minRating,
        @RequestParam(value = "maxRating", required = false) Integer maxRating,
        @RequestParam(value = "minDuration", required = false) Integer minDuration,
        @RequestParam(value = "maxDuration", required = false) Integer maxDuration,
        @RequestParam(value = "includeSubOnly", defaultValue = "false") boolean includeSubOnly,
        @RequestParam(value = "map", required = false) Integer map
    )
    {
        if(dateCursor == null) dateCursor = SC2Pulse.offsetDateTime();
        return new CursorNavigableResult<>(ladderMatchDAO.findTwitchVods
        (
            race, versusRace,
            minRating, maxRating,
            minDuration, maxDuration,
            includeSubOnly,
            map,
            dateCursor,
            typeCursor,
            mapCursor,
            regionCursor,
            2,
            1
        ).getResult(), new CursorNavigation(null, null));
    }

}
