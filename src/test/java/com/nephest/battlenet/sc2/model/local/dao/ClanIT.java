// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static com.nephest.battlenet.sc2.model.local.ClanMemberEvent.EventType.JOIN;
import static com.nephest.battlenet.sc2.model.local.ClanMemberEvent.EventType.LEAVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.ClanMember;
import com.nephest.battlenet.sc2.model.local.ClanMemberEvent;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.inner.ClanMemberEventData;
import com.nephest.battlenet.sc2.model.local.ladder.LadderClanMemberEvents;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.web.service.ClanService;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = AllTestConfig.class)
@AutoConfigureMockMvc
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
    private PlayerCharacterStatsDAO playerCharacterStatsDAO;

    @Autowired
    private ClanService clanService;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private JdbcTemplate template;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mvc;

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
        Clan clan1 = clanDAO.merge(Set.of(new Clan(null, "clan1", Region.EU, "clan1Name")))
            .iterator().next();
        Clan clan2 = clanDAO.merge(Set.of(new Clan(null, "clan2", Region.EU, "clan2Name")))
            .iterator().next();
        Set<ClanMember> cm1 = template
            .queryForList("SELECT id FROM player_character", Long.class)
            .stream()
            .map(id->new ClanMember(id, clan1.getId()))
            .collect(Collectors.toSet());
        clanMemberDAO.merge(cm1);

        //only clans with new stats are updated
        assertEquals(1, clanDAO.updateStats(List.of(clan1.getId(), clan2.getId())));

        List<Clan> clans = clanDAO.findByIds(Set.of(clan1.getId(), clan2.getId()));
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
        Set<ClanMember> cm2 = template.queryForList
        (
            "SELECT id "
            + "FROM player_character "
            + "WHERE id < " + ClanDAO.CLAN_STATS_MIN_MEMBERS + 1,
            Long.class
        )
            .stream()
            .map(id->new ClanMember(id, clan1.getId()))
            .collect(Collectors.toSet());
        clanMemberDAO.merge(cm2);
        assertEquals(0, clanDAO.nullifyStats(ClanDAO.CLAN_STATS_MIN_MEMBERS - 1));
        Clan clan1WithStatsValid = clanDAO.findByIds(Set.of(clan1.getId())).get(0);
        assertEquals(14, clan1WithStatsValid.getMembers()); //all players
        assertEquals(7, clan1WithStatsValid.getActiveMembers()); //7-14 teams
        assertEquals(10, clan1WithStatsValid.getAvgRating()); // (7 + 8 + 9 + 10 + 11 + 12 + 13) / 6
        assertEquals(BaseLeague.LeagueType.PLATINUM, clan1WithStatsValid.getAvgLeagueType()); // (7 + 8 + 9 + 10 + 11 + 12 + 13) / 6
        assertEquals(7, clan1WithStatsValid.getGames()); //7-14 teams

        //invalid stats are nullified
        template.execute("DELETE FROM clan_member");
        Set<ClanMember> cm3 = template.queryForList
        (
            "SELECT id "
            + "FROM player_character "
            + "WHERE id < " + ClanDAO.CLAN_STATS_MIN_MEMBERS,
            Long.class
        )
            .stream()
            .map(id->new ClanMember(id, clan1.getId()))
            .collect(Collectors.toSet());
        clanMemberDAO.merge(cm3);
        assertEquals(1, clanDAO.nullifyStats(ClanDAO.CLAN_STATS_MIN_MEMBERS - 1));
        Clan clan1WithStatsNullified = clanDAO.findByIds(Set.of(clan1.getId())).get(0);
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
            clanDAO.merge(Set.of(new Clan(null, "tag" + i, Region.EU, "name" + i)));
        for(int i = 0; i < 5; i++)
        {
            final int fi = i;
            Set<ClanMember> cm = template.queryForList
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
                .collect(Collectors.toSet());
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

    @Test
    public void testSaveClans()
    throws Exception
    {
        seasonGenerator.generateDefaultSeason(10);
        playerCharacterStatsDAO.mergeCalculate();
        Clan clan = new Clan(1, "tag123", Region.EU, "name");
        Instant now = Instant.now();
        clanService.saveClans(List.of(
            new ClanMemberEventData(new PlayerCharacter(1L, 1L, Region.EU, 1L, 1, "name"), clan, now),
            new ClanMemberEventData(new PlayerCharacter(2L, 2L, Region.EU, 2L, 2, "name"), clan, now),
            new ClanMemberEventData(new PlayerCharacter(3L, 3L, Region.EU, 3L, 3, "name"), clan, now)
        ));

        LadderDistinctCharacter[] chars = objectMapper.readValue(mvc.perform
        (
            get("/api/character/search")
                .queryParam("term", "[" + clan.getTag() + "]")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), LadderDistinctCharacter[].class);
        assertEquals(3, chars.length);
        assertEquals(3L, chars[0].getMembers().getCharacter().getId());
        assertEquals(2L, chars[1].getMembers().getCharacter().getId());
        assertEquals(1L, chars[2].getMembers().getCharacter().getId());

        LadderClanMemberEvents evts = objectMapper.readValue(mvc.perform
        (
            get("/api/group/clan/history")
                .queryParam("clanId", String.valueOf(clan.getId()))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), LadderClanMemberEvents.class);
        evts.getCharacters().sort(Comparator.comparing(c->c.getMembers().getCharacter().getId()));
        Assertions.assertThat(evts)
            .usingRecursiveComparison()
            .ignoringFields("events.created")
            .isEqualTo
            (
                new LadderClanMemberEvents
                (
                    List.of
                    (
                        SeasonGenerator.defaultLadderCharacter(clan, null, null, null, 0),
                        SeasonGenerator.defaultLadderCharacter(clan, null, null, null, 1),
                        SeasonGenerator.defaultLadderCharacter(clan, null, null, null, 2)
                    ),
                    List.of(clan),
                    List.of
                    (
                        new ClanMemberEvent
                        (
                            3L,
                            clan.getId(),
                            JOIN,
                            null,
                            null
                        ),
                        new ClanMemberEvent
                        (
                            2L,
                            clan.getId(),
                            JOIN,
                            null,
                            null
                        ),
                        new ClanMemberEvent
                        (
                            1L,
                            clan.getId(),
                            JOIN,
                            null,
                            null
                        )
                    )
                )
            );
    }

    @Test
    public void whenUpdateUsingOldData_thenFilterIt()
    throws Exception
    {
        Clan clan1 = new Clan(1, "tag123", Region.EU, "name");
        Clan clan2 = new Clan(2, "tag1234", Region.EU, "name");
        Clan clan3 = new Clan(3, "tag12345", Region.EU, "name");
        PlayerCharacter pChar = new PlayerCharacter(1L, 1L, Region.EU, 1L, 1, "name");
        seasonGenerator.generateDefaultSeason(1);
        playerCharacterStatsDAO.mergeCalculate();
        Instant start = Instant.now().minusSeconds(60);
        clanService.removeClanUpdates();
        clanService.saveClans(List.of(new ClanMemberEventData(pChar, clan1, start)));
        clanService.saveClans(List.of(new ClanMemberEventData(pChar, clan2, start.minusSeconds(1))));
        clanService.saveClans(List.of(new ClanMemberEventData(pChar, clan3, start.plusSeconds(1))));

        LadderClanMemberEvents evts = objectMapper.readValue(mvc.perform
        (
            get("/api/group/clan/history")
                .queryParam("characterId", "1")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), LadderClanMemberEvents.class);
        evts.getClans().sort(Comparator.comparing(Clan::getTag));
        Assertions.assertThat(evts)
            .usingRecursiveComparison()
            .ignoringFields("events.created", "events.secondsSincePrevious")
            .isEqualTo
            (
                new LadderClanMemberEvents
                (
                    List.of
                    (
                        SeasonGenerator.defaultLadderCharacter(clan3, null, null, null, 0)
                    ),
                    List.of(clan1, clan3),
                    List.of
                    (
                        new ClanMemberEvent
                        (
                            1L,
                            clan3.getId(),
                            JOIN,
                            null,
                            null
                        ),
                        new ClanMemberEvent
                        (
                            1L,
                            clan1.getId(),
                            LEAVE,
                            null,
                            null
                        ),
                        new ClanMemberEvent
                        (
                            1L,
                            clan1.getId(),
                            JOIN,
                            null,
                            null
                        )
                    )
                )
            );
    }

    @Test
    public void whenUpdatingFromNullToNonNullName_thenUpdate()
    {
        Clan nullNameClan = clanDAO.merge(Set.of(new Clan(null, "tag", Region.EU, null)))
            .iterator().next();
        clanDAO.merge(Set.of(new Clan(null, "tag", Region.EU, "name"))); //update name
        Clan foundClan = clanDAO.findByIds(Set.of(nullNameClan.getId())).get(0);
        assertEquals("name", foundClan.getName());
    }

    @Test
    public void whenExpiredMembersRemoved_thenGenerateClanMemberEvents()
    throws Exception
    {
        seasonGenerator.generateDefaultSeason(3);
        playerCharacterStatsDAO.mergeCalculate();
        PlayerCharacter[] chars = LongStream.range(0, 3)
            .boxed()
            .map(i->i + 1)
            .map(i->new PlayerCharacter(i, i, null, null, null, null))
            .toArray(PlayerCharacter[]::new);
        Clan[] clans = IntStream.range(0, chars.length)
            .boxed()
            .map(i->i + 1)
            .map(i->new Clan(i, "tag" + i, Region.EU, "name"))
            .toArray(Clan[]::new);
        Instant now = Instant.now();
        List<ClanMemberEventData> clanData = IntStream.range(0, chars.length)
            .boxed()
            .map(i->new ClanMemberEventData(chars[i], clans[i], now))
            .collect(Collectors.toList());
        clanService.saveClans(clanData);

        template.update
        (
            "UPDATE clan_member "
                + "SET updated = NOW() - INTERVAL '" + ClanMemberDAO.TTL.toHours() +  " hours' "
                + "WHERE player_character_id IN(" + chars[0].getId() + ", " + chars[1].getId() + ")"
        );
        clanService.removeExpiredClanMembers();

        LadderClanMemberEvents evts = objectMapper.readValue(mvc.perform
        (
            get("/api/group/clan/history")
                .queryParam
                (
                    "characterId",
                    Arrays.stream(chars)
                        .map(PlayerCharacter::getId)
                        .map(String::valueOf)
                        .toArray(String[]::new)
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), LadderClanMemberEvents.class);
        evts.getEvents().sort(Comparator.comparing(ClanMemberEvent::getPlayerCharacterId)
            .thenComparing(ClanMemberEvent::getCreated, Comparator.reverseOrder()));
        evts.getClans().sort(Comparator.comparing(Clan::getTag));
        Assertions.assertThat(evts)
            .usingRecursiveComparison()
            .ignoringFields("events.created", "events.secondsSincePrevious")
            .isEqualTo
            (
                new LadderClanMemberEvents
                (
                    List.of
                    (
                        SeasonGenerator.defaultLadderCharacter(clans[2], null, null, null, 2),
                        SeasonGenerator.defaultLadderCharacter(null, null, null, null, 1),
                        SeasonGenerator.defaultLadderCharacter(null, null, null, null, 0)
                    ),
                    Arrays.asList(clans),
                    List.of
                    (
                        new ClanMemberEvent
                        (
                            chars[0].getId(),
                            clans[0].getId(),
                            LEAVE,
                            null,
                            null
                        ),
                        new ClanMemberEvent
                        (
                            chars[0].getId(),
                            clans[0].getId(),
                            JOIN,
                            null,
                            null
                        ),
                        new ClanMemberEvent
                        (
                            chars[1].getId(),
                            clans[1].getId(),
                            LEAVE,
                            null,
                            null
                        ),
                        new ClanMemberEvent
                        (
                            chars[1].getId(),
                            clans[1].getId(),
                            JOIN,
                            null,
                            null
                        ),
                        new ClanMemberEvent
                        (
                            chars[2].getId(),
                            clans[2].getId(),
                            JOIN,
                            null,
                            null
                        )
                    )
                )
            );
    }

}
