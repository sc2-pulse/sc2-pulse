// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.config.openapi.TeamLegacyUids;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistoryDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.web.controller.group.TeamGroup;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/api/team/group")
public class TeamGroupControllerLegacy
{

    private static final int HISTORY_TEAM_COUNT_MAX = TeamController.HISTORY_TEAM_COUNT_MAX;
    private static final int LAST_TEAM_IN_GROUP_LEGACY_UID_COUNT_MAX
        = TeamController.LAST_TEAM_IN_GROUP_LEGACY_UID_COUNT_MAX;

    @Autowired
    private LadderSearchDAO ladderSearchDAO;

    @Autowired
    private TeamHistoryDAO teamHistoryDAO;

    @GetMapping("/flat") @TeamGroup
    public ResponseEntity<Object> getCharacterIds(@TeamGroup Set<Long> teamIds)
    {
        return WebServiceUtil.notFoundIfEmpty(teamIds);
    }

    @GetMapping("/team/full") @TeamGroup
    public ResponseEntity<?> getLadderTeams(@TeamGroup Set<Long> teamIds)
    {
        return WebServiceUtil.notFoundIfEmpty(ladderSearchDAO.findTeamsByIds(teamIds));
    }

    @GetMapping("/team/last/full")
    public ResponseEntity<?> getLastLadderTeams
    (
        @RequestParam("legacyUid")
        @TeamLegacyUids
        @Valid
        @Size(max = LAST_TEAM_IN_GROUP_LEGACY_UID_COUNT_MAX)
        Set<TeamLegacyUid> legacyUids
    )
    {
        return WebServiceUtil.notFoundIfEmpty(ladderSearchDAO.findLegacyTeams(legacyUids, false));
    }

    private static Optional<ResponseEntity<Object>> getHistoryParametersError
    (
        Set<TeamHistoryDAO.StaticColumn> staticColumns,
        TeamHistoryDAO.GroupMode groupMode,
        OffsetDateTime from,
        OffsetDateTime to
    )
    {
        ResponseEntity<Object> result = null;
        if(from != null && to != null && !from.isBefore(to))
            result = ResponseEntity.of(ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "'from' parameter must be before 'to' parameter"))
                        .build();
        if(staticColumns.stream().anyMatch(c->!groupMode.isSupported(c)))
            result = ResponseEntity.of(ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST,
                    "Some static columns are not supported by the group mode"))
                        .build();

        return Optional.ofNullable(result);
    }


    @GetMapping("/history") @TeamGroup
    public ResponseEntity<Object> getHistory
    (
        @TeamGroup @Size(max = HISTORY_TEAM_COUNT_MAX) Set<Long> teamIds,
        @RequestParam("history") Set<TeamHistoryDAO.HistoryColumn> historyColumns,
        @RequestParam(value = "static", defaultValue = "") Set<TeamHistoryDAO.StaticColumn> staticColumns,
        @RequestParam(value = "groupBy", defaultValue = TeamHistoryDAO.GroupMode.NAMES.TEAM)
        TeamHistoryDAO.GroupMode groupMode,
        @RequestParam(value = "from", required = false) OffsetDateTime from,
        @RequestParam(value = "to", required = false) OffsetDateTime to
    )
    {

        return getHistoryParametersError(staticColumns, groupMode, from , to)
            .orElseGet(()->WebServiceUtil.notFoundIfEmpty(
                teamHistoryDAO.find(teamIds, from, to, staticColumns, historyColumns, groupMode)));
    }

    @GetMapping("/history/summary") @TeamGroup
    public ResponseEntity<Object> getHistorySummary
    (
        @TeamGroup @Size(max = HISTORY_TEAM_COUNT_MAX) Set<Long> teamIds,
        @RequestParam("summary") Set<TeamHistoryDAO.SummaryColumn> summaryColumns,
        @RequestParam(value = "static", defaultValue = "") Set<TeamHistoryDAO.StaticColumn> staticColumns,
        @RequestParam(value = "groupBy", defaultValue = TeamHistoryDAO.GroupMode.NAMES.TEAM)
        TeamHistoryDAO.GroupMode groupMode,
        @RequestParam(value = "from", required = false) OffsetDateTime from,
        @RequestParam(value = "to", required = false) OffsetDateTime to
    )
    {
        return getHistoryParametersError(staticColumns, groupMode, from , to)
            .orElseGet(()->WebServiceUtil.notFoundIfEmpty(
                teamHistoryDAO.findSummary(teamIds, from, to, staticColumns, summaryColumns, groupMode)));
    }

}
