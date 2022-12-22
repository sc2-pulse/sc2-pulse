// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Evidence;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterReport;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class PlayerCharacterReportDBIT
{

    @Autowired
    private PlayerCharacterReportDAO playerCharacterReportDAO;

    @Autowired
    private EvidenceDAO evidenceDAO;

    @BeforeEach
    public void beforeEach
    (
        @Autowired DataSource dataSource,
        @Autowired SeasonGenerator seasonGenerator
        )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
            seasonGenerator.generateDefaultSeason
            (
                List.of(Region.EU),
                List.of(BaseLeague.LeagueType.BRONZE),
                List.of(QueueType.LOTV_1V1),
                TeamType.ARRANGED, BaseLeagueTier.LeagueTierType.FIRST, 10
            );
        }

    }

    @AfterEach
    public void afterEach(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }

    @Test
    public void testFindReportByCursorId()
    {
        PlayerCharacterReport report1 = playerCharacterReportDAO.merge(new PlayerCharacterReport(
            null, 1L, null, PlayerCharacterReport.PlayerCharacterReportType.CHEATER,
            false, OffsetDateTime.now().minusDays(PlayerCharacterReportDAO.DENIED_REPORT_TTL_DAYS)));
        PlayerCharacterReport report2 = playerCharacterReportDAO.merge(new PlayerCharacterReport(
            null, 2L, null, PlayerCharacterReport.PlayerCharacterReportType.CHEATER,
            false, OffsetDateTime.now().minusDays(PlayerCharacterReportDAO.DENIED_REPORT_TTL_DAYS)));
        PlayerCharacterReport report3 = playerCharacterReportDAO.merge(new PlayerCharacterReport(
            null, 3L, null, PlayerCharacterReport.PlayerCharacterReportType.CHEATER,
            false, OffsetDateTime.now().minusDays(PlayerCharacterReportDAO.DENIED_REPORT_TTL_DAYS)));

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
            false, OffsetDateTime.now().minusDays(PlayerCharacterReportDAO.DENIED_REPORT_TTL_DAYS)));
        Evidence evidence1 = evidenceDAO.create(new Evidence(
            null, report.getId(), null, localhost, "description asda",false,
            OffsetDateTime.now().minusDays(EvidenceDAO.DENIED_EVIDENCE_TTL_DAYS) ,OffsetDateTime.now()));
        Evidence evidence2 = evidenceDAO.create(new Evidence(
            null, report.getId(), null, localhost, "description asda",false,
            OffsetDateTime.now().minusDays(EvidenceDAO.DENIED_EVIDENCE_TTL_DAYS) ,OffsetDateTime.now()));
        Evidence evidence3 = evidenceDAO.create(new Evidence(
            null, report.getId(), null, localhost, "description asda",false,
            OffsetDateTime.now().minusDays(EvidenceDAO.DENIED_EVIDENCE_TTL_DAYS) ,OffsetDateTime.now()));

        List<Evidence> evidences = evidenceDAO.findByIdCursor(1, 2);
        assertEquals(2, evidences.size());
        assertEquals(evidence2.getId(), evidences.get(0).getId());
        assertEquals(evidence3.getId(), evidences.get(1).getId());
    }

}
