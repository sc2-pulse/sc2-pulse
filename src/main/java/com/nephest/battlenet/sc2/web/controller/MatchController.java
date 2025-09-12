// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMatch;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderMatchDAO;
import com.nephest.battlenet.sc2.model.navigation.Cursor;
import com.nephest.battlenet.sc2.model.validation.CursorNavigableResult;
import com.nephest.battlenet.sc2.model.validation.Version;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MatchController
{

    @Autowired
    private LadderMatchDAO ladderMatchDAO;

    @GetMapping(value = "/matches", params = "vod")
    public CursorNavigableResult<List<LadderMatch>> getVods
    (
        @Version(LadderMatchDAO.CURSOR_POSITION_VERSION) Cursor cursor,
        @RequestParam(value = "race", required = false) Race race,
        @RequestParam(value = "raceVersus", required = false) Race versusRace,
        @RequestParam(value = "ratingMin", required = false) Integer minRating,
        @RequestParam(value = "ratingMax", required = false) Integer maxRating,
        @RequestParam(value = "durationMin", required = false) Integer minDuration,
        @RequestParam(value = "durationMax", required = false) Integer maxDuration,
        @RequestParam(value = "includeSubOnly", defaultValue = "false") boolean includeSubOnly,
        @RequestParam(value = "mapId", required = false) Integer map
    )
    {
        return ladderMatchDAO.findTwitchVods
        (
            race, versusRace,
            minRating, maxRating,
            minDuration, maxDuration,
            includeSubOnly,
            map,
            cursor
        );
    }

}
