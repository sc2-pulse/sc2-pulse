// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import static com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO.CURSOR_POSITION_VERSION;

import com.nephest.battlenet.sc2.config.openapi.TeamLegacyUids;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.IdField;
import com.nephest.battlenet.sc2.model.IdProjection;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.inner.RawTeamHistoryHistoryData;
import com.nephest.battlenet.sc2.model.local.inner.RawTeamHistoryStaticData;
import com.nephest.battlenet.sc2.model.local.inner.RawTeamHistorySummaryData;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistory;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistoryDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistorySummary;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.model.navigation.Cursor;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.model.validation.AllowedField;
import com.nephest.battlenet.sc2.model.validation.CursorNavigableResult;
import com.nephest.battlenet.sc2.model.validation.Version;
import com.nephest.battlenet.sc2.model.web.SortParameter;
import com.nephest.battlenet.sc2.web.controller.group.TeamGroup;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
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

@RestController
@RequestMapping("/api")
public class TeamController
{

    public static final int RECENT_TEAMS_LIMIT = 250;
    public static final Duration RECENT_TEAMS_OFFSET = Duration.ofMinutes(60);
    public static final int HISTORY_TEAM_COUNT_MAX = 1200;
    public static final int LAST_TEAM_IN_GROUP_LEGACY_UID_COUNT_MAX = 100;

    @Autowired
    private LadderSearchDAO ladderSearchDAO;

    @Autowired
    private TeamHistoryDAO teamHistoryDAO;

    private static Optional<ResponseEntity<?>> getHistoryParametersError
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

    @GetMapping(value = "/teams", params = "recent")
    public List<LadderTeam> getTeams
    (
        @RequestParam("queue") QueueType queueType,
        @RequestParam("league") BaseLeague.LeagueType league,
        @RequestParam(value = "region", required = false) Region region,
        @RequestParam(value = "race", required = false) Race race,
        @RequestParam(value = "winsMin", required = false) @Valid @Min(0) Integer winsMin,
        @RequestParam(value = "winsMax", required = false)  @Valid @Min(0) Integer winsMax,
        @RequestParam(value = "ratingMin", required = false) @Valid @Min(0) Integer ratingMin,
        @RequestParam(value = "ratingMax", required = false) @Valid @Min(0) Integer ratingMax,
        @RequestParam(value = "limit", defaultValue = RECENT_TEAMS_LIMIT + "") @Valid @Min(1) @Max(RECENT_TEAMS_LIMIT) int limit
    )
    {
        return ladderSearchDAO.findRecentlyActiveTeams
        (
            queueType,
            league,
            SC2Pulse.offsetDateTime().minus(RECENT_TEAMS_OFFSET),
            winsMin, winsMax,
            ratingMin, ratingMax,
            race,
            region,
            limit
        );
    }

    @GetMapping("/teams") @TeamGroup
    public ResponseEntity<?> getTeams
    (
        @TeamGroup Set<Long> teamIds,
        @RequestParam(value = "field", required = false) @AllowedField(IdField.NAME) String field
    )
    {
        return field != null
            ? ResponseEntity.ok(teamIds.stream().map(IdProjection::new).toList())
            : ResponseEntity.ok(ladderSearchDAO.findTeamsByIds(teamIds));
    }

    @Operation(description = "Returns last team from each `teamLegacyUid` set")
    @GetMapping(value = "/teams", params = "last")
    public List<LadderTeam> getLastTeams
    (
        @RequestParam("teamLegacyUid")
        @TeamLegacyUids
        @Valid
        @Size(max = LAST_TEAM_IN_GROUP_LEGACY_UID_COUNT_MAX)
        Set<TeamLegacyUid> legacyUids
    )
    {
        return ladderSearchDAO.findLegacyTeams(legacyUids, false);
    }

    @Operation(summary = "Ladder")
    @GetMapping(value = "/teams", params = {"queue", "season"})
    public CursorNavigableResult<List<LadderTeam>> getLadders
    (
        @RequestParam("queue") QueueType queue,
        @RequestParam("teamType") TeamType teamType,
        @RequestParam("season") @Min(0) int season,
        @RequestParam(value = "region", defaultValue = "") Set<Region> regions,
        @RequestParam(value = "league", defaultValue = "") Set<BaseLeague.LeagueType> leagues,
        @RequestParam(value = "sort", defaultValue = "-rating")
        @AllowedField("rating") SortParameter sort,
        @Version(CURSOR_POSITION_VERSION) Cursor cursor
    )
    {
        return ladderSearchDAO.find
        (
            season,
            regions,
            leagues,
            queue,
            teamType,
            sort,
            cursor
        );
    }

    @GetMapping("/team-histories") @TeamGroup
    public List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> getHistories
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
        WebServiceUtil.throwException
        (
            getHistoryParametersError(staticColumns, groupMode, from , to)
                .orElse(null)
        );
        return teamHistoryDAO.find(teamIds, from, to, staticColumns, historyColumns, groupMode);
    }

    @GetMapping("/team-history-summaries") @TeamGroup
    public List<TeamHistorySummary<RawTeamHistoryStaticData, RawTeamHistorySummaryData>> getHistorySummaries
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
        WebServiceUtil.throwException
        (
            getHistoryParametersError(staticColumns, groupMode, from , to)
                .orElse(null)
        );
        return teamHistoryDAO.findSummary
        (
            teamIds,
            from,
            to,
            staticColumns,
            summaryColumns,
            groupMode
        );
    }

}
