// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.config.openapi.TeamLegacyUids;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamState;
import com.nephest.battlenet.sc2.model.local.ladder.common.CommonTeamHistory;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderTeamStateDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Validated
@RestController
@RequestMapping("/api/team")
public class TeamController
{

    public static final int RECENT_TEAMS_LIMIT = 250;
    public static final Duration RECENT_TEAMS_OFFSET = Duration.ofMinutes(60);

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

    @GetMapping("/history/common")
    public Map<String, CommonTeamHistory> getCommonHistory
    (@RequestParam("legacyUid") @TeamLegacyUids Set<TeamLegacyUid> ids)
    {
        if(ids == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "legacyUid parameter not found");

        List<LadderTeamState> states = ladderTeamStateDAO.find(ids);
        List<LadderTeam> teams = ladderSearchDAO.findLegacyTeams(ids, true);
        return groupCommonHistory(ids, states, teams);
    }

    private Map<String, CommonTeamHistory> groupCommonHistory
    (Set<TeamLegacyUid> ids, List<LadderTeamState> states, List<LadderTeam> teams)
    {
        Map<String, CommonTeamHistory> result = new HashMap<>();

        for(TeamLegacyUid id : ids)
        {
            List<LadderTeam> filteredTeams = teams.stream()
                .filter(t->t.getQueueType() == id.getQueueType()
                    && t.getRegion() == id.getRegion()
                    && t.getLegacyId().equals(id.getId()))
                .collect(Collectors.toList());
            Set<Long> teamIds = filteredTeams.stream().map(LadderTeam::getId).collect(Collectors.toSet());
            List<LadderTeamState> filteredStates = states.stream()
                .filter(s->teamIds.contains(s.getTeamState().getTeamId()))
                .collect(Collectors.toList());

            teams.removeAll(filteredTeams);
            states.removeAll(filteredStates);

            result.put(mvcConversionService.convert(id, String.class), new CommonTeamHistory(filteredTeams, filteredStates));
        }

        return result;
    }

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
        @RequestParam(value = "limit", defaultValue = RECENT_TEAMS_LIMIT + "") @Valid @Min(1) @Max(RECENT_TEAMS_LIMIT) int limit,
        @RequestParam(value = "recent", defaultValue = "true") boolean recent
    )
    {
        if(!recent) return ResponseEntity.badRequest().body("Only recent teams are supported");

        return WebServiceUtil.notFoundIfEmpty(ladderSearchDAO.findRecentlyActiveTeams
        (
            queueType,
            league,
            SC2Pulse.offsetDateTime().minus(RECENT_TEAMS_OFFSET),
            winsMin, winsMax,
            ratingMin, ratingMax,
            race,
            region,
            limit
        ));
    }

}
