// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.local.Evidence;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterReport;
import com.nephest.battlenet.sc2.model.local.dao.EvidenceDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterReportDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderEvidence;
import com.nephest.battlenet.sc2.model.local.ladder.LadderEvidenceVote;
import com.nephest.battlenet.sc2.model.local.ladder.LadderPlayerCharacterReport;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderEvidenceVoteDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderPlayerCharacterReportDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderTeamMemberDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

@Service
public class PlayerCharacterReportService
{

    public static final int EVIDENCE_PER_DAY = 10;
    public static final int CONFIRMED_EVIDENCE_MAX = 3;

    private final PlayerCharacterReportDAO playerCharacterReportDAO;
    private final EvidenceDAO evidenceDAO;
    private final LadderEvidenceVoteDAO ladderEvidenceVoteDAO;
    private final LadderPlayerCharacterReportDAO ladderPlayerCharacterReportDAO;
    private final LadderTeamMemberDAO ladderTeamMemberDAO;

    @Autowired
    public PlayerCharacterReportService
    (
        PlayerCharacterReportDAO playerCharacterReportDAO,
        EvidenceDAO evidenceDAO,
        LadderEvidenceVoteDAO ladderEvidenceVoteDAO,
        LadderPlayerCharacterReportDAO ladderPlayerCharacterReportDAO,
        LadderTeamMemberDAO ladderTeamMemberDAO
    )
    {
        this.playerCharacterReportDAO = playerCharacterReportDAO;
        this.evidenceDAO = evidenceDAO;
        this.ladderEvidenceVoteDAO = ladderEvidenceVoteDAO;
        this.ladderPlayerCharacterReportDAO = ladderPlayerCharacterReportDAO;
        this.ladderTeamMemberDAO = ladderTeamMemberDAO;
    }

    @Transactional
    public int reportCharacter
    (
        Long id,
        Long additionalId,
        PlayerCharacterReport.PlayerCharacterReportType type,
        String evidence,
        byte[] reporterIp,
        Long reporterId
    )
    {
        if(evidenceDAO.getCount(reporterIp, reporterId, OffsetDateTime.now().minusDays(1)) >= EVIDENCE_PER_DAY) return -2;
        PlayerCharacterReport report = playerCharacterReportDAO.merge(new PlayerCharacterReport(
            null,
            id,
            additionalId,
            type,
            null,
            OffsetDateTime.now()
        ));
        if(evidenceDAO.getConfirmedCount(report.getId()) >= CONFIRMED_EVIDENCE_MAX) return -3;

        return evidenceDAO.create(new Evidence(
            null, report.getId(), reporterId, reporterIp, evidence, null, OffsetDateTime.now(), OffsetDateTime.now()
        )).getId();
    }

    public List<LadderPlayerCharacterReport> findReports()
    {
        List<LadderPlayerCharacterReport> reports = ladderPlayerCharacterReportDAO.findAll();
        Map<Long, List<LadderTeamMember>> additionalMembers = ladderTeamMemberDAO.findByCharacterIds(reports.stream()
            .map(r->r.getReport().getAdditionalPlayerCharacterId())
            .filter(Objects::nonNull)
            .toArray(Long[]::new)).stream()
                .collect(groupingBy(m->m.getCharacter().getId()));
        Map<Integer, List<Evidence>> evidences = evidenceDAO.findAll(true).stream()
            .collect(groupingBy(Evidence::getPlayerCharacterReportId));
        Map<Integer, List<LadderEvidenceVote>> evidenceVotes = ladderEvidenceVoteDAO.findAll().stream()
            .collect(groupingBy(v->v.getVote().getEvidenceId()));
        Comparator<LadderPlayerCharacterReport> comparator = Comparator.comparing(r->r.getEvidence().stream()
            .max(Comparator.comparing(e->e.getEvidence().getCreated())).get().getEvidence().getCreated());
        reports
            .forEach(r->
            {
                r.setEvidence(evidences.getOrDefault(r.getReport().getId(), List.of()).stream()
                    .map(e->new LadderEvidence(e, evidenceVotes.getOrDefault(e.getId(), List.of()))).collect(Collectors.toList()));
                if(r.getReport().getAdditionalPlayerCharacterId() != null)
                    r.setAdditionalMember(additionalMembers.get(r.getReport().getAdditionalPlayerCharacterId()).get(0));
            });
        reports.sort(comparator.reversed());
        return reports;
    }

    public List<LadderPlayerCharacterReport> findReportsByCharacterId(long characterId)
    {
        List<LadderPlayerCharacterReport> reports = ladderPlayerCharacterReportDAO.findByCharacterId(characterId);
        Map<Long, List<LadderTeamMember>> additionalMembers = ladderTeamMemberDAO.findByCharacterIds(reports.stream()
            .map(r->r.getReport().getAdditionalPlayerCharacterId())
            .filter(Objects::nonNull)
            .toArray(Long[]::new)).stream()
                .collect(groupingBy(m->m.getCharacter().getId()));
        Map<Integer, List<Evidence>> evidences = evidenceDAO
            .findByReportIds(true, reports.stream().map(r->r.getReport().getId()).toArray(Integer[]::new)).stream()
            .collect(groupingBy(Evidence::getPlayerCharacterReportId));
        Map<Integer, List<LadderEvidenceVote>> evidenceVotes =
            ladderEvidenceVoteDAO.findByEvidenceIds(evidences.values().stream().flatMap(l->l.stream().map(Evidence::getId)).toArray(Integer[]::new)).stream()
            .collect(groupingBy(v->v.getVote().getEvidenceId()));
        Comparator<LadderPlayerCharacterReport> comparator = Comparator.comparing(r->r.getEvidence().stream()
            .max(Comparator.comparing(e->e.getEvidence().getCreated())).get().getEvidence().getCreated());
        reports
            .forEach(r->
            {
                r.setEvidence(evidences.getOrDefault(r.getReport().getId(), List.of()).stream()
                    .map(e->new LadderEvidence(e, evidenceVotes.getOrDefault(e.getId(), List.of()))).collect(Collectors.toList()));
                if(r.getReport().getAdditionalPlayerCharacterId() != null)
                    r.setAdditionalMember(additionalMembers.get(r.getReport().getAdditionalPlayerCharacterId()).get(0));
            });
        reports.sort(comparator.reversed());
        return reports;
    }


    @Transactional
    public void update(OffsetDateTime from)
    {
        evidenceDAO.updateStatus(from);
        playerCharacterReportDAO.updateStatus(from);
        evidenceDAO.evictRequiredVotesCache();
        evidenceDAO.getRequiredVotes();
        playerCharacterReportDAO.removeExpired();
        evidenceDAO.removeExpired();
    }

}
