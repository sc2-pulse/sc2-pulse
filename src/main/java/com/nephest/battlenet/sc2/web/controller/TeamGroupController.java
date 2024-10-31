// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.local.inner.TeamHistoryDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.web.controller.group.TeamGroup;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/team/group")
public class TeamGroupController
{

    public static final int HISTORY_TEAM_COUNT_MAX = 1200;

    @Autowired
    private LadderSearchDAO ladderSearchDAO;

    @Autowired
    private TeamHistoryDAO teamHistoryDAO;

    @GetMapping("/flat") @TeamGroup
    public ResponseEntity<Set<Long>> getCharacterIds(@TeamGroup Set<Long> teamIds)
    {
        return WebServiceUtil.notFoundIfEmpty(teamIds);
    }

    @GetMapping("/teams/full") @TeamGroup
    public ResponseEntity<?> getLadderTeams(@TeamGroup Set<Long> teamIds)
    {
        return WebServiceUtil.notFoundIfEmpty(ladderSearchDAO.findTeamsByIds(teamIds));
    }

    @GetMapping("/history") @TeamGroup
    public ResponseEntity<?> getHistory
    (
        @TeamGroup @Size(max = HISTORY_TEAM_COUNT_MAX) Set<Long> teamIds,
        @RequestParam("history") Set<TeamHistoryDAO.HistoryColumn> historyColumns,
        @RequestParam(value = "static", defaultValue = "") Set<TeamHistoryDAO.StaticColumn> staticColumns,
        @RequestParam(value = "from", required = false) OffsetDateTime from,
        @RequestParam(value = "to", required = false) OffsetDateTime to
    )
    {
        if(from != null && to != null && !from.isBefore(to))
            return ResponseEntity.of(ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "'from' parameter must be before 'to' parameter"))
                    .build();

        return WebServiceUtil.notFoundIfEmpty(
            teamHistoryDAO.find(teamIds, from, to, staticColumns, historyColumns));
    }

}
