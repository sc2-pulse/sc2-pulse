// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.ClanMember;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;

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
    private ClanMemberDAO clanMemberDAO;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private JdbcTemplate template;

    @BeforeEach
    public void beforeEach(@Autowired DataSource dataSource, @Autowired CacheManager cacheManager)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
            cacheManager.getCacheNames()
                .forEach(cacheName->cacheManager.getCache(cacheName).clear());
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
        LocalDate now = LocalDate.now();
        seasonGenerator.generateSeason
        (
            List.of
            (
                //this season is excluded from stats because it's too old
                new Season(null, 1, Region.EU, 2020, 1,
                    now.minusDays(ClanDAO.CLAN_STATS_DEPTH_DAYS + 11), now.minusDays(ClanDAO.CLAN_STATS_DEPTH_DAYS + 1)),
                new Season(null, 2, Region.EU, 2020, 2,
                    now.minusDays(ClanDAO.CLAN_STATS_DEPTH_DAYS - 2), now.plusDays(10))
            ),
            List.of(BaseLeague.LeagueType.values()),
            List.of(QueueType.LOTV_1V1), TeamType.ARRANGED, BaseLeagueTier.LeagueTierType.FIRST,
            1
        );
        Clan clan1 = clanDAO.merge(new Clan(null, "clan1", Region.EU, "clan1Name"))[0];
        Clan clan2 = clanDAO.merge(new Clan(null, "clan2", Region.EU, "clan2Name"))[0];
        ClanMember[] cm1 = template
            .queryForList("SELECT id FROM player_character", Long.class)
            .stream()
            .map(id->new ClanMember(id, clan1.getId()))
            .toArray(ClanMember[]::new);
        clanMemberDAO.merge(cm1);

        //only clans with new stats are updated
        assertEquals(1, clanDAO.updateStats(List.of(clan1.getId(), clan2.getId())));

        List<Clan> clans = clanDAO.findByIds(clan1.getId(), clan2.getId());
        clans.sort(Comparator.comparing(Clan::getId));

        /*
            SeasonGenerator generates incremental legacy ids, and player character summary uses legacy ids for its
            race filter. In this case only teams with ids in range 1-4(race ids) will be counted as active
         */
        Clan clan1WithStats = clans.get(0);
        assertEquals(14, clan1WithStats.getMembers()); //all players
        assertEquals(7, clan1WithStats.getActiveMembers()); //7-14 teams
        assertEquals(10, clan1WithStats.getAvgRating()); // (7 + 8 + 9 + 10 + 11 + 12 + 13) / 6
        assertEquals(BaseLeague.LeagueType.PLATINUM, clan1WithStats.getAvgLeagueType()); // (7 + 8 + 9 + 10 + 11 + 12 + 13) / 6
        assertEquals(7, clan1WithStats.getGames()); //7-14 teams

        Clan clan2WithStats = clans.get(1);
        assertNull(clan2WithStats.getMembers());
        assertNull(clan2WithStats.getActiveMembers());
        assertNull(clan2WithStats.getAvgRating());
        assertNull(clan2WithStats.getAvgLeagueType());
        assertNull(clan2WithStats.getGames());

        //valid stats are not nullified
        template.execute("DELETE FROM clan_member");
        ClanMember[] cm2 = template.queryForList
        (
            "SELECT id "
            + "FROM player_character "
            + "WHERE id < " + ClanDAO.CLAN_STATS_MIN_MEMBERS + 1,
            Long.class
        )
            .stream()
            .map(id->new ClanMember(id, clan1.getId()))
            .toArray(ClanMember[]::new);
        clanMemberDAO.merge(cm2);
        assertEquals(0, clanDAO.nullifyStats(ClanDAO.CLAN_STATS_MIN_MEMBERS - 1));
        Clan clan1WithStatsValid = clanDAO.findByIds(clan1.getId()).get(0);
        assertEquals(14, clan1WithStatsValid.getMembers()); //all players
        assertEquals(7, clan1WithStatsValid.getActiveMembers()); //7-14 teams
        assertEquals(10, clan1WithStatsValid.getAvgRating()); // (7 + 8 + 9 + 10 + 11 + 12 + 13) / 6
        assertEquals(BaseLeague.LeagueType.PLATINUM, clan1WithStatsValid.getAvgLeagueType()); // (7 + 8 + 9 + 10 + 11 + 12 + 13) / 6
        assertEquals(7, clan1WithStatsValid.getGames()); //7-14 teams

        //invalid stats are nullified
        template.execute("DELETE FROM clan_member");
        ClanMember[] cm3 = template.queryForList
        (
            "SELECT id "
            + "FROM player_character "
            + "WHERE id < " + ClanDAO.CLAN_STATS_MIN_MEMBERS,
            Long.class
        )
            .stream()
            .map(id->new ClanMember(id, clan1.getId()))
            .toArray(ClanMember[]::new);
        clanMemberDAO.merge(cm3);
        assertEquals(1, clanDAO.nullifyStats(ClanDAO.CLAN_STATS_MIN_MEMBERS - 1));
        Clan clan1WithStatsNullified = clanDAO.findByIds(clan1.getId()).get(0);
        assertNull(clan1WithStatsNullified.getMembers());
        assertNull(clan1WithStatsNullified.getActiveMembers());
        assertNull(clan1WithStatsNullified.getAvgRating());
        assertNull(clan1WithStatsNullified.getAvgLeagueType());
        assertNull(clan1WithStatsNullified.getGames());
    }

    @Test
    public void testFindByMinMemberCount()
    {
        seasonGenerator.generateDefaultSeason
        (
            List.of(Region.EU),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            ClanDAO.CLAN_STATS_MIN_MEMBERS * 10
        );

        for(int i = 0; i < 10; i++)
            clanDAO.merge(new Clan(null, "tag" + i, Region.EU, "name" + i));
        for(int i = 0; i < 5; i++)
        {
            final int fi = i;
            ClanMember[] cm = template.queryForList
            (
                String.format
                (
                    "SELECT id "
                    + "FROM player_character "
                    + "WHERE id > %1$s AND id <= %2$s",
                    i * ClanDAO.CLAN_STATS_MIN_MEMBERS,
                    (i + 1) * ClanDAO.CLAN_STATS_MIN_MEMBERS
                ),
                Long.class
            )
                .stream()
                .map(id->new ClanMember(id, fi + 1))
                .toArray(ClanMember[]::new);
            clanMemberDAO.merge(cm);
        }


        Set<Integer> validClans =
            new HashSet<>(clanDAO.findIdsByMinMemberCount(ClanDAO.CLAN_STATS_MIN_MEMBERS));
        assertEquals(5, validClans.size());
        for(int i = 1; i < 6; i++) assertTrue(validClans.contains(i));

        assertEquals(validClans.size(), clanDAO.getCountByMinMemberCount(ClanDAO.CLAN_STATS_MIN_MEMBERS));

        List<Integer> page1 = clanDAO
            .findIdsByMinMemberCount(ClanDAO.CLAN_STATS_MIN_MEMBERS, 0, 2);
        assertEquals(2, page1.size());
        assertEquals(1, page1.get(0));
        assertEquals(2, page1.get(1));

        List<Integer> page2 = clanDAO
            .findIdsByMinMemberCount(ClanDAO.CLAN_STATS_MIN_MEMBERS, 2, 10);
        assertEquals(3, page2.size());
        assertEquals(3, page2.get(0));
        assertEquals(4, page2.get(1));
        assertEquals(5, page2.get(2));
    }

}
