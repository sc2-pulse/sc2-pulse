// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.config.security.AccountUser;
import com.nephest.battlenet.sc2.model.local.Evidence;
import com.nephest.battlenet.sc2.model.local.EvidenceVote;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterReport;
import com.nephest.battlenet.sc2.model.local.dao.EvidenceDAO;
import com.nephest.battlenet.sc2.model.local.dao.EvidenceVoteDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderEvidenceVote;
import com.nephest.battlenet.sc2.model.local.ladder.LadderPlayerCharacterReport;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderEvidenceVoteDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.web.service.PlayerCharacterReportService;
import io.swagger.v3.oas.annotations.Hidden;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Hidden
@RestController
@RequestMapping("/api/character/report")
public class PlayerCharacterReportController
{

    @Autowired
    private PlayerCharacterReportService reportService;

    @Autowired
    private EvidenceVoteDAO evidenceVoteDAO;

    @Autowired
    private EvidenceDAO evidenceDAO;

    @Autowired
    private LadderEvidenceVoteDAO ladderEvidenceVoteDAO;

    @GetMapping("/list")
    public List<LadderPlayerCharacterReport> findReports()
    {
        return reportService.findReports();
    }

    @GetMapping("/list/{id}")
    public List<LadderPlayerCharacterReport> findReportsByCharacterIds(@PathVariable("id") Set<Long> ids)
    {
        return reportService.findReportsByCharacterIds(ids);
    }

    @PostMapping("/new")
    public ResponseEntity<String> reportPlayerCharacter
    (
        @AuthenticationPrincipal AccountUser user,
        @RequestParam(name = "playerCharacterId") Long characterId,
        @RequestParam(name = "additionalPlayerCharacterId", required = false) Long additionalCharacterId,
        @RequestParam(name = "type") PlayerCharacterReport.PlayerCharacterReportType type,
        @RequestParam(name = "evidence") @Valid @NotBlank @Size(max=Evidence.MAX_LENGTH) String evidence,
        @Autowired HttpServletRequest request
    )
    throws UnknownHostException
    {
        int result = reportService.reportCharacter(characterId, additionalCharacterId, type, evidence,
            InetAddress.getByName(request.getRemoteAddr()).getAddress(),
            user == null ? null : user.getAccount().getId());
        switch (result)
        {
            case -2:
                return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("{\"message\":\"Reports per day cap reached\"}");
            case -3:
                return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body("{\"message\":\"Evidence per report cap reached\"}");
            default:
                return ResponseEntity
                    .status(HttpStatus.OK)
                    .body("{\"message\":\"" + result + "\"}");
        }
    }

    @PostMapping("/vote/{evidenceId}/{vote}")
    public List<LadderEvidenceVote> vote
    (
        @AuthenticationPrincipal AccountUser user,
        @PathVariable(name = "evidenceId") Integer evidenceId,
        @PathVariable(name = "vote") Boolean vote
    )
    {
        OffsetDateTime evidenceCreated = evidenceDAO.findById(false, evidenceId).orElseThrow().getCreated();
        evidenceVoteDAO.merge(new EvidenceVote(
            evidenceId,
            evidenceCreated,
            user.getAccount().getId(),
            vote,
            SC2Pulse.offsetDateTime()
        ));
        return ladderEvidenceVoteDAO.findByEvidenceIds(Set.of(evidenceId));
    }

}
