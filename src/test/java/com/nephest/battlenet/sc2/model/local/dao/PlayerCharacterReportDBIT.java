// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.extension.AutoConfigureDatabase;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Evidence;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterReport;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@AutoConfigureDatabase
public class PlayerCharacterReportDBIT
{

    @Autowired
    private PlayerCharacterReportDAO playerCharacterReportDAO;

    @Autowired
    private EvidenceDAO evidenceDAO;

    @BeforeEach
    public void beforeEach
    (
        @Autowired SeasonGenerator seasonGenerator
        )
    throws Exception
    {
        seasonGenerator.generateDefaultSeason
        (
            List.of(Region.EU),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED, BaseLeagueTier.LeagueTierType.FIRST, 10
        );

    }

    @Test
    public void testFindReportByCursorId()
    {
        PlayerCharacterReport report1 = playerCharacterReportDAO.merge(new PlayerCharacterReport(
            null, 1L, null, PlayerCharacterReport.PlayerCharacterReportType.CHEATER,
            false, false,
            SC2Pulse.offsetDateTime()));
        PlayerCharacterReport report2 = playerCharacterReportDAO.merge(new PlayerCharacterReport(
            null, 2L, null, PlayerCharacterReport.PlayerCharacterReportType.CHEATER,
            false, false,
            SC2Pulse.offsetDateTime()));
        PlayerCharacterReport report3 = playerCharacterReportDAO.merge(new PlayerCharacterReport(
            null, 3L, null, PlayerCharacterReport.PlayerCharacterReportType.CHEATER,
            false, false,
            SC2Pulse.offsetDateTime()));

        List<PlayerCharacterReport> reports = playerCharacterReportDAO.findByIdCursor(1, 2);
        assertEquals(2, reports.size());
        assertEquals(report2, reports.get(0));
        assertEquals(report3, reports.get(1));
    }

    @Test
    public void testFindEvidenceByCursorId()
    throws UnknownHostException
    {
        byte[] localhost = InetAddress.getByName("127.0.0.1").getAddress();
        PlayerCharacterReport report = playerCharacterReportDAO.merge(new PlayerCharacterReport(
            null, 1L, null, PlayerCharacterReport.PlayerCharacterReportType.CHEATER,
            false, false,
            SC2Pulse.offsetDateTime()));
        Evidence evidence1 = evidenceDAO.create(new Evidence(
            null, report.getId(), null, localhost, "description asda",false,
            SC2Pulse.offsetDateTime().minusDays(EvidenceDAO.UNTIL_ARCHIVED_DAYS) ,SC2Pulse.offsetDateTime()));
        Evidence evidence2 = evidenceDAO.create(new Evidence(
            null, report.getId(), null, localhost, "description asda",false,
            SC2Pulse.offsetDateTime().minusDays(EvidenceDAO.UNTIL_ARCHIVED_DAYS) ,SC2Pulse.offsetDateTime()));
        Evidence evidence3 = evidenceDAO.create(new Evidence(
            null, report.getId(), null, localhost, "description asda",false,
            SC2Pulse.offsetDateTime().minusDays(EvidenceDAO.UNTIL_ARCHIVED_DAYS) ,SC2Pulse.offsetDateTime()));

        List<Evidence> evidences = evidenceDAO.findByIdCursor(1, 2);
        assertEquals(2, evidences.size());
        assertEquals(evidence2.getId(), evidences.get(0).getId());
        assertEquals(evidence3.getId(), evidences.get(1).getId());
    }

}
