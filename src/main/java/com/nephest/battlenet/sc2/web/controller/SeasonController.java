// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.local.Period;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderSeasonState;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSeasonStateDAO;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/season")
public class SeasonController
{

    @Autowired
    private LadderSeasonStateDAO ladderSeasonStateDAO;

    @Autowired
    private LadderSearchDAO ladderSearch;

    @Autowired
    private SeasonDAO seasonDAO;

    @GetMapping("/list")
    public List<Season> getSeasons()
    {
        return ladderSearch.findSeasonList();
    }

    @GetMapping("/list/all")
    public List<Season> getAllSeasons(@RequestParam(name = "season", required = false) Integer season)
    {
        return seasonDAO.findListByBattlenetId(season);
    }

    @GetMapping("/state/{to}/{period}")
    public List<LadderSeasonState> getState(@PathVariable("to") OffsetDateTime to, @PathVariable("period") Period period)
    {
        return ladderSeasonStateDAO.find(to, period);
    }

}
