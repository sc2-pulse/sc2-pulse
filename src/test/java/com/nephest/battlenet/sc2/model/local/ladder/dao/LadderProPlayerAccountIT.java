// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.ProPlayerAccount;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerAccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerDAO;
import com.nephest.battlenet.sc2.model.local.ladder.RevealerStats;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
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
public class LadderProPlayerAccountIT
{

    @Autowired
    private ProPlayerDAO proPlayerDAO;

    @Autowired
    private ProPlayerAccountDAO proPlayerAccountDAO;

    @Autowired
    private LadderProPlayerAccountDAO ladderProPlayerAccountDAO;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @BeforeEach
    public void beforeEach
    (
        @Autowired DataSource dataSource
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
    }

    @AfterAll
    public static void afterAll(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }

    @Test
    public void testFindRevealerStats()
    {
        ProPlayer proPlayer = proPlayerDAO.merge(new ProPlayer(null, 1L, "tag", "name"));
        Account[] accounts = seasonGenerator.generateAccounts(Partition.GLOBAL, "btag", 10);
        PlayerCharacter[] characters = seasonGenerator
            .generateCharacters("name", accounts, Region.EU, 1L);
        OffsetDateTime odt = OffsetDateTime.now();
        proPlayerAccountDAO.merge
        (
            false,
            new ProPlayerAccount(proPlayer.getId(), accounts[0].getId(), accounts[0].getId(), odt, false),
            new ProPlayerAccount(proPlayer.getId(), accounts[1].getId(), accounts[0].getId(), odt, false),
            new ProPlayerAccount(proPlayer.getId(), accounts[2].getId(), accounts[0].getId(), odt, false),

            new ProPlayerAccount(proPlayer.getId(), accounts[3].getId(), accounts[1].getId(), odt, false),
            new ProPlayerAccount(proPlayer.getId(), accounts[4].getId(), accounts[1].getId(), odt, false),

            new ProPlayerAccount(proPlayer.getId(), accounts[5].getId(), accounts[2].getId(), odt, false)
        );

        List<RevealerStats> stats = ladderProPlayerAccountDAO.findRevealerStats(2);
        assertEquals(2, stats.size());

        RevealerStats stats1 = stats.get(0);
        assertEquals(accounts[0], stats1.getRevealer());
        assertEquals(3, stats1.getAccountsRevealed());

        RevealerStats stats2 = stats.get(1);
        assertEquals(accounts[1], stats2.getRevealer());
        assertEquals(2, stats2.getAccountsRevealed());
    }

}
