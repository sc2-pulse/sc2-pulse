// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(classes = AllTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class ClanIT
{

    @Autowired
    private ClanDAO clanDAO;

    @Autowired
    private PlayerCharacterDAO playerCharacterDAO;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private JdbcTemplate template;

    @BeforeEach
    public void beforeEach(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
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
    public void testStatsCalculation()
    {
        seasonGenerator.generateSeason
        (
            List.of(new Season(null, 1, Region.EU, 2020, 1,
                LocalDate.now().minusDays(ClanDAO.CLAN_STATS_DEPTH_DAYS - 2), LocalDate.now().plusDays(10))),
            List.of(BaseLeague.LeagueType.values()),
            List.of(QueueType.LOTV_1V1), TeamType.ARRANGED, BaseLeagueTier.LeagueTierType.FIRST,
            1
        );
        Clan clan1 = clanDAO.merge(new Clan(null, "clan1", Region.EU, "clan1Name"))[0];
        Clan clan2 = clanDAO.merge(new Clan(null, "clan2", Region.EU, "clan2Name"))[0];

        playerCharacterDAO.findTopPlayerCharacters
        (
            1,
            QueueType.LOTV_1V1,
            TeamType.ARRANGED,
            100,
            null
        ).getResult().stream()
            .peek(c->c.setClanId(clan1.getId()))
            .forEach(playerCharacterDAO::merge);

        List<Integer> validClans = clanDAO.findIdsByMinMemberCount(ClanDAO.CLAN_STATS_MIN_MEMBERS);
        assertEquals(1, validClans.size());
        assertEquals(clan1.getId(), validClans.get(0));

        //only clans with new stats are updated
        assertEquals(1, clanDAO.updateStats());

        List<Clan> clans = clanDAO.findByIds(clan1.getId(), clan2.getId());
        clans.sort(Comparator.comparing(Clan::getId));

        /*
            SeasonGenerator generates incremental legacy ids, and player character summary uses legacy ids for its
            race filter. In this case only teams with ids in range 1-4(race ids) will be counted as active
         */
        Clan clan1WithStats = clans.get(0);
        assertEquals(7, clan1WithStats.getMembers()); //all players
        assertEquals(4, clan1WithStats.getActiveMembers()); //1-4 teams
        assertEquals(3, clan1WithStats.getAvgRating()); // (1 + 2 + 3 + 4) / 4
        assertEquals(BaseLeague.LeagueType.PLATINUM, clan1WithStats.getAvgLeagueType()); // (1 + 2 + 3 + 4) / 4
        assertEquals(4, clan1WithStats.getGames()); //1-4 teams

        Clan clan2WithStats = clans.get(1);
        assertNull(clan2WithStats.getMembers());
        assertNull(clan2WithStats.getActiveMembers());
        assertNull(clan2WithStats.getAvgRating());
        assertNull(clan2WithStats.getAvgLeagueType());
        assertNull(clan2WithStats.getGames());

        //valid stats are not nullified
        template.execute("UPDATE player_character SET clan_id = NULL");
        template.execute("UPDATE player_character SET clan_id = 1 WHERE id < " + ClanDAO.CLAN_STATS_MIN_MEMBERS + 1);
        assertEquals(0, clanDAO.nullifyStats(ClanDAO.CLAN_STATS_MIN_MEMBERS - 1));
        Clan clan1WithStatsValid = clanDAO.findByIds(clan1.getId()).get(0);
        assertEquals(7, clan1WithStatsValid.getMembers()); //all players
        assertEquals(4, clan1WithStatsValid.getActiveMembers()); //1-4 teams
        assertEquals(3, clan1WithStatsValid.getAvgRating()); // (1 + 2 + 3 + 4) / 4
        assertEquals(BaseLeague.LeagueType.PLATINUM, clan1WithStatsValid.getAvgLeagueType()); // (1 + 2 + 3 + 4) / 4
        assertEquals(4, clan1WithStatsValid.getGames()); //1-4 teams

        //invalid stats are nullified
        template.execute("UPDATE player_character SET clan_id = NULL");
        template.execute("UPDATE player_character SET clan_id = 1 WHERE id < " + ClanDAO.CLAN_STATS_MIN_MEMBERS );
        assertEquals(1, clanDAO.nullifyStats(ClanDAO.CLAN_STATS_MIN_MEMBERS - 1));
        Clan clan1WithStatsNullified = clanDAO.findByIds(clan1.getId()).get(0);
        assertNull(clan1WithStatsNullified.getMembers());
        assertNull(clan1WithStatsNullified.getActiveMembers());
        assertNull(clan1WithStatsNullified.getAvgRating());
        assertNull(clan1WithStatsNullified.getAvgLeagueType());
        assertNull(clan1WithStatsNullified.getGames());
    }

}
