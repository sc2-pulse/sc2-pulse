// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.extension.AutoConfigureDatabase;
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
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = AllTestConfig.class)
@TestPropertySource("classpath:application.properties")
@AutoConfigureDatabase
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

    @Test
    public void testFindRevealerStats()
    {
        ProPlayer proPlayer = proPlayerDAO.merge(new ProPlayer(null, 1L, "tag", "name"));
        Account[] accounts = seasonGenerator.generateAccounts(Partition.GLOBAL, "btag", 10);
        PlayerCharacter[] characters = seasonGenerator
            .generateCharacters("name", accounts, Region.EU, 1L);
        OffsetDateTime odt = SC2Pulse.offsetDateTime();
        proPlayerAccountDAO.merge
        (
            false,
            Set.of
            (
                new ProPlayerAccount(proPlayer.getId(), accounts[0].getId(), accounts[0].getId(), odt, false),
                new ProPlayerAccount(proPlayer.getId(), accounts[1].getId(), accounts[0].getId(), odt, false),
                new ProPlayerAccount(proPlayer.getId(), accounts[2].getId(), accounts[0].getId(), odt, false),

                new ProPlayerAccount(proPlayer.getId(), accounts[3].getId(), accounts[1].getId(), odt, false),
                new ProPlayerAccount(proPlayer.getId(), accounts[4].getId(), accounts[1].getId(), odt, false),

                new ProPlayerAccount(proPlayer.getId(), accounts[5].getId(), accounts[2].getId(), odt, false)
            )
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
