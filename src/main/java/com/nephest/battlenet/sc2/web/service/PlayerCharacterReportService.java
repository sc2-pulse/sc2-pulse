// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static java.util.stream.Collectors.groupingBy;

import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Evidence;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterReport;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.EvidenceDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterReportDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderEvidence;
import com.nephest.battlenet.sc2.model.local.ladder.LadderEvidenceVote;
import com.nephest.battlenet.sc2.model.local.ladder.LadderPlayerCharacterReport;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderEvidenceVoteDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderPlayerCharacterReportDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderTeamMemberDAO;
import com.nephest.battlenet.sc2.util.MarkdownUtil;
import com.nephest.battlenet.sc2.web.service.notification.NotificationService;
import com.nephest.battlenet.sc2.web.util.WebContextUtil;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerCharacterReportService
{

    public static final Set<SC2PulseAuthority> NOTIFICATION_ROLES = Set.of
    (
        SC2PulseAuthority.ADMIN,
        SC2PulseAuthority.MODERATOR
    );
    public static final Set<SC2PulseAuthority> SECURE_ROLES = Set.of
    (
        SC2PulseAuthority.ADMIN,
        SC2PulseAuthority.MODERATOR
    );
    public static final Set<String> SECURE_ROLE_NAMES = SECURE_ROLES.stream()
        .map(SC2PulseAuthority::getAuthority)
        .collect(Collectors.toSet());

    public static final int EVIDENCE_PER_DAY = 10;
    public static final int CONFIRMED_EVIDENCE_MAX = 3;

    private final PlayerCharacterReportDAO playerCharacterReportDAO;
    private final EvidenceDAO evidenceDAO;
    private final LadderEvidenceVoteDAO ladderEvidenceVoteDAO;
    private final LadderPlayerCharacterReportDAO ladderPlayerCharacterReportDAO;
    private final LadderTeamMemberDAO ladderTeamMemberDAO;
    private final AccountDAO accountDAO;
    private final PlayerCharacterDAO playerCharacterDAO;
    private final NotificationService notificationService;
    private final PersonalService personalService;
    private final String characterUrlTemplate;

    @Autowired
    public PlayerCharacterReportService
    (
        PlayerCharacterReportDAO playerCharacterReportDAO,
        EvidenceDAO evidenceDAO,
        LadderEvidenceVoteDAO ladderEvidenceVoteDAO,
        LadderPlayerCharacterReportDAO ladderPlayerCharacterReportDAO,
        LadderTeamMemberDAO ladderTeamMemberDAO,
        AccountDAO accountDAO,
        PlayerCharacterDAO playerCharacterDAO,
        NotificationService notificationService,
        PersonalService personalService,
        WebContextUtil webContextUtil
    )
    {
        this.playerCharacterReportDAO = playerCharacterReportDAO;
        this.evidenceDAO = evidenceDAO;
        this.ladderEvidenceVoteDAO = ladderEvidenceVoteDAO;
        this.ladderPlayerCharacterReportDAO = ladderPlayerCharacterReportDAO;
        this.ladderTeamMemberDAO = ladderTeamMemberDAO;
        this.accountDAO = accountDAO;
        this.playerCharacterDAO = playerCharacterDAO;
        this.notificationService = notificationService;
        this.personalService = personalService;
        this.characterUrlTemplate = webContextUtil.getCharacterUrlTemplate() + "#player-stats-player";
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
            false,
            OffsetDateTime.now()
        ));
        if(evidenceDAO.getConfirmedCount(report.getId()) >= CONFIRMED_EVIDENCE_MAX) return -3;

        Evidence evidenceObj = evidenceDAO.create(new Evidence(
            null, report.getId(), reporterId, reporterIp, evidence, null, OffsetDateTime.now(), OffsetDateTime.now()
        ));
        enqueueNotifications(report, evidenceObj);
        return evidenceObj.getId();
    }

    private void enqueueNotifications(PlayerCharacterReport report, Evidence evidence)
    {
        Set<Long> notificationRecipientIds = NOTIFICATION_ROLES.stream()
            .map(accountDAO::findByRole)
            .flatMap(List::stream)
            .map(Account::getId)
            .collect(Collectors.toSet());
        PlayerCharacter accusedPlayer1 =
            playerCharacterDAO.find(report.getPlayerCharacterId()).get(0);
        PlayerCharacter accusedPlayer2 = report.getAdditionalPlayerCharacterId() != null
            ? playerCharacterDAO.find(report.getAdditionalPlayerCharacterId()).get(0)
            : null;
        notificationRecipientIds.add(accusedPlayer1.getAccountId());
        if(accusedPlayer2 != null) notificationRecipientIds.add(accusedPlayer2.getAccountId());
        if(evidence.getReporterAccountId() != null)
            notificationRecipientIds.add(evidence.getReporterAccountId());

        String msg = renderReportNotification(report, evidence, accusedPlayer1, accusedPlayer2);

        notificationService.enqueueNotifications(msg, notificationRecipientIds.toArray(Long[]::new));
    }

    private String renderReportNotification
    (
        PlayerCharacterReport report,
        Evidence evidence,
        PlayerCharacter accusedPlayer1,
        PlayerCharacter accusedPlayer2
    )
    {
        StringBuilder sb = new StringBuilder("**New player report received**\n");

        sb.append("**Reporter:** ");
        if(evidence.getReporterAccountId() != null)
        {
            Account reporter = accountDAO.findByIds(evidence.getReporterAccountId()).get(0);
            sb.append("BattleTag: ")
                .append(reporter.getBattleTag())
                .append("\n");
        }
        else
        {
            sb.append("anonymous\n");
        }

        sb.append("**Accused player:** ")
            .append(MarkdownUtil.renderLink(accusedPlayer1,
                String.format(characterUrlTemplate, accusedPlayer1.getId())))
            .append("\n");
        if(accusedPlayer2 != null)
        {
            sb.append("**Accused player2:** ")
                .append(MarkdownUtil.renderLink(accusedPlayer2,
                    String.format(characterUrlTemplate, accusedPlayer2.getId())))
                .append("\n");
        }
        sb.append("**Accusations:** ").append(report.getType()).append("\n");
        sb.append("**Evidence:** ")
            .append(evidence.getDescription()).append("\n\n");
        sb.append("*You received this notification because you are a moderator, accused player, "
            + "or original reporter*\n");

        return sb.toString();
    }

    public static List<LadderPlayerCharacterReport> clearSensitiveData
    (List<LadderPlayerCharacterReport> reports, Authentication authentication)
    {
        if
        (
            authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(SECURE_ROLE_NAMES::contains)
        ) return reports;

        reports.stream()
            .map(LadderPlayerCharacterReport::getEvidence)
            .flatMap(Collection::stream)
            .map(LadderEvidence::getVotes)
            .flatMap(Collection::stream)
            .forEach(PlayerCharacterReportService::clearSensitiveData);
        return reports;
    }

    public static void clearSensitiveData(LadderEvidenceVote vote)
    {
        vote.getVote().setVoterAccountId(null);
        vote.setVoterAccount(null);
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
        Map<Long, List<Account>> reporters = getReporters(evidences);
        Map<Integer, List<LadderEvidenceVote>> evidenceVotes = ladderEvidenceVoteDAO.findAll().stream()
            .collect(groupingBy(v->v.getVote().getEvidenceId()));
        Comparator<LadderPlayerCharacterReport> comparator = Comparator.comparing(r->r.getEvidence().stream()
            .max(Comparator.comparing(e->e.getEvidence().getCreated())).get().getEvidence().getCreated());
        reports
            .forEach(r->
            {
                r.setEvidence(evidences.getOrDefault(r.getReport().getId(), List.of()).stream()
                    .map(e->new LadderEvidence(e, evidenceVotes.getOrDefault(e.getId(), List.of()),
                        e.getReporterAccountId() == null ? null : reporters.get(e.getReporterAccountId()).get(0)))
                    .collect(Collectors.toList()));
                if(r.getReport().getAdditionalPlayerCharacterId() != null)
                    r.setAdditionalMember(additionalMembers.get(r.getReport().getAdditionalPlayerCharacterId()).get(0));
            });
        if(!reports.isEmpty()) reports.sort(comparator.reversed());
        return clearSensitiveData(reports, personalService.getAuthentication());
    }

    public List<LadderPlayerCharacterReport> findReportsByCharacterIds(Long... characterIds)
    {
        List<LadderPlayerCharacterReport> reports = ladderPlayerCharacterReportDAO.findByCharacterIds(characterIds);
        Map<Long, List<LadderTeamMember>> additionalMembers = ladderTeamMemberDAO.findByCharacterIds(reports.stream()
            .map(r->r.getReport().getAdditionalPlayerCharacterId())
            .filter(Objects::nonNull)
            .toArray(Long[]::new)).stream()
                .collect(groupingBy(m->m.getCharacter().getId()));
        Map<Integer, List<Evidence>> evidences = evidenceDAO
            .findByReportIds(true, reports.stream().map(r->r.getReport().getId()).toArray(Integer[]::new)).stream()
            .collect(groupingBy(Evidence::getPlayerCharacterReportId));
        Map<Long, List<Account>> reporters = getReporters(evidences);
        Map<Integer, List<LadderEvidenceVote>> evidenceVotes =
            ladderEvidenceVoteDAO.findByEvidenceIds(evidences.values().stream().flatMap(l->l.stream().map(Evidence::getId)).toArray(Integer[]::new)).stream()
            .collect(groupingBy(v->v.getVote().getEvidenceId()));
        Comparator<LadderPlayerCharacterReport> comparator = Comparator.comparing(r->r.getEvidence().stream()
            .max(Comparator.comparing(e->e.getEvidence().getCreated())).get().getEvidence().getCreated());
        reports
            .forEach(r->
            {
                r.setEvidence(evidences.getOrDefault(r.getReport().getId(), List.of()).stream()
                    .map(e->new LadderEvidence(e, evidenceVotes.getOrDefault(e.getId(), List.of()),
                        e.getReporterAccountId() == null ? null : reporters.get(e.getReporterAccountId()).get(0)))
                    .collect(Collectors.toList()));
                if(r.getReport().getAdditionalPlayerCharacterId() != null)
                    r.setAdditionalMember(additionalMembers.get(r.getReport().getAdditionalPlayerCharacterId()).get(0));
            });
        if(!reports.isEmpty()) reports.sort(comparator.reversed());
        return clearSensitiveData(reports, personalService.getAuthentication());
    }

    private Map<Long, List<Account>> getReporters(Map<Integer, List<Evidence>> evidences)
    {
        return accountDAO.findByIds(evidences.values().stream()
            .flatMap(Collection::stream)
            .map(Evidence::getReporterAccountId)
            .filter(Objects::nonNull)
            .distinct()
            .toArray(Long[]::new)).stream()
                .collect(groupingBy(Account::getId));
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
