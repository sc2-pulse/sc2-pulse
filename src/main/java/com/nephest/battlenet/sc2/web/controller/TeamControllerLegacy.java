// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderTeamStateDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/api/team")
public class TeamControllerLegacy
{

    @Autowired
    private LadderTeamStateDAO ladderTeamStateDAO;

    @Autowired
    private LadderSearchDAO ladderSearchDAO;

    @Autowired
    private TeamDAO teamDAO;

    @Autowired @Qualifier("sc2StatsConversionService")
    private ConversionService conversionService;

    @Autowired @Qualifier("mvcConversionService")
    private ConversionService mvcConversionService;

    @GetMapping("")
    public ResponseEntity<?> getRecentTeams
    (
        @RequestParam("queue") QueueType queueType,
        @RequestParam("league") BaseLeague.LeagueType league,
        @RequestParam(value = "winsMin", required = false) @Valid @Min(0) Integer winsMin,
        @RequestParam(value = "winsMax", required = false)  @Valid @Min(0) Integer winsMax,
        @RequestParam(value = "ratingMin", required = false) @Valid @Min(0) Integer ratingMin,
        @RequestParam(value = "ratingMax", required = false) @Valid @Min(0) Integer ratingMax,
        @RequestParam(value = "race", required = false) Race race,
        @RequestParam(value = "region", required = false) Region region,
        @RequestParam(value = "limit", defaultValue = TeamController.RECENT_TEAMS_LIMIT + "") @Valid @Min(1) @Max(TeamController.RECENT_TEAMS_LIMIT) int limit,
        @RequestParam(value = "recent", defaultValue = "true") boolean recent
    )
    {
        if(!recent) return ResponseEntity.badRequest().body("Only recent teams are supported");

        return WebServiceUtil.notFoundIfEmpty(ladderSearchDAO.findRecentlyActiveTeams
        (
            queueType,
            league,
            SC2Pulse.offsetDateTime().minus(TeamController.RECENT_TEAMS_OFFSET),
            winsMin, winsMax,
            ratingMin, ratingMax,
            race,
            region,
            limit
        ));
    }

}
