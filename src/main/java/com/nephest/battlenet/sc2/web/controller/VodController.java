// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMatch;
import com.nephest.battlenet.sc2.model.local.ladder.PagedSearchResult;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderMatchDAO;
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

    @GetMapping
    ({
        "/twitch/search/{dateAnchor}/{typeAnchor}/{mapAnchor}/{regionAnchor}/{page}/{pageDiff}",
        "/twitch/search/{dateAnchor}/{typeAnchor}/{mapAnchor}/{page}/{pageDiff}",
    })
    public PagedSearchResult<List<LadderMatch>> getCharacterMatches
    (
        @PathVariable("dateAnchor") String dateAnchor,
        @PathVariable("typeAnchor") BaseMatch.MatchType typeAnchor,
        @PathVariable("mapAnchor") int mapAnchor,
        @PathVariable(value = "regionAnchor", required = false) Region regionAnchor,
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
            OffsetDateTime.parse(dateAnchor),
            typeAnchor,
            mapAnchor,
            regionAnchor == null ? Region.US : regionAnchor,
            page,
            pageDiff
        );
    }

}
