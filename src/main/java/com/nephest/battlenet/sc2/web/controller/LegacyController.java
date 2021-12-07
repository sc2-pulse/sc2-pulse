// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class LegacyController
{

    @Autowired
    private LadderSearchDAO ladderSearch;

    @GetMapping("/seasons")
    public List<Season> getSeasons()
    {
        return ladderSearch.findSeasonList();
    }

}
