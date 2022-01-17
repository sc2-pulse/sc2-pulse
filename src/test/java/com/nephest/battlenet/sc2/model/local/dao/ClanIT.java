// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.ladder.PagedSearchResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Autowired
    private ObjectMapper objectMapper;

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
    }

    @Test
    public void testSearch(@Autowired WebApplicationContext webApplicationContext)
    throws Exception
    {
        int clanCount = ClanDAO.PAGE_SIZE * 2;

        Clan[] clans = new Clan[clanCount];
        for(int i = 0; i < clanCount; i++)
            clans[i] = new Clan(null, "clan" + i, Region.EU, "clan" + i + "Name");
        clanDAO.merge(clans);
        template.execute
        (
            "UPDATE clan "
            + "SET active_members = id, "
            + "avg_rating = id + 1, "
            + "games = id * 2"
        );

        MockMvc mvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .alwaysDo(print())
            .build();

        testIdsSearch(mvc);
        testTagSearch(mvc);
        testCursorSearch(mvc, ClanDAO.Cursor.ACTIVE_MEMBERS, clanCount, 1, clanCount);
        testCursorSearch(mvc, ClanDAO.Cursor.AVG_RATING, clanCount + 1, 2, clanCount);
    }

    private void testIdsSearch(MockMvc mvc)
    throws Exception
    {
        Clan[] clans = objectMapper.readValue(mvc.perform
        (
            get("/api/clan/id/1,5,7")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        assertEquals(3, clans.length);
        Arrays.sort(clans, Comparator.comparing(Clan::getId));
        assertEquals(1, clans[0].getId());
        assertEquals(5, clans[1].getId());
        assertEquals(7, clans[2].getId());
    }

    private void testTagSearch(MockMvc mvc)
    throws Exception
    {
        Clan[] clans = objectMapper.readValue(mvc.perform
        (
            get("/api/clan/tag/clan1")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        assertEquals(1, clans.length);
        Clan clan = clans[0];
        assertNotNull(clan);
        assertEquals(2, clan.getId());
        assertEquals("clan1", clan.getTag());
        assertEquals("clan1Name", clan.getName());
    }

    private void testCursorSearch(MockMvc mvc, ClanDAO.Cursor cursor, int max, int min, int clanCount)
    throws Exception
    {
        //normal, first page
        PagedSearchResult<List<Clan>> result = objectMapper.readValue(mvc.perform
        (
            get("/api/clan/cursor/{cursor}/{maxCursor}/{maxCursor}/0/1", cursor, max + 1, max + 1)
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        assertEquals(ClanDAO.PAGE_SIZE, result.getResult().size());
        for(int i = 0; i < ClanDAO.PAGE_SIZE; i++)
            assertEquals(clanCount - i, result.getResult().get(i).getId());

        //reversed, last page
        PagedSearchResult<List<Clan>> reversedResult = objectMapper.readValue(mvc.perform
        (
            get("/api/clan/cursor/{cursor}/{minCursor}/{minCursor}/2/-1", cursor, min - 1, min - 1)
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        assertEquals(ClanDAO.PAGE_SIZE, reversedResult.getResult().size());
        for(int i = 0; i < ClanDAO.PAGE_SIZE; i++)
            assertEquals(ClanDAO.PAGE_SIZE - i, reversedResult.getResult().get(i).getId());

        //filtered by active member count
        PagedSearchResult<List<Clan>> filteredByActiveMembersResult = objectMapper.readValue(mvc.perform
        (
            get("/api/clan/cursor/{cursor}/{maxCursor}/{maxCursor}/0/1"
                + "?minActiveMembers={min}&maxActiveMembers={max}",
                cursor, max, max, 10, 19
            )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        assertEquals(10, filteredByActiveMembersResult.getResult().size());
        for(int i = 0; i < filteredByActiveMembersResult.getResult().size(); i++)
            assertEquals(19 - i, filteredByActiveMembersResult.getResult().get(i).getId());

        //filtered by active avg rating
        PagedSearchResult<List<Clan>> filteredByAvgRatingResult = objectMapper.readValue(mvc.perform
        (
            get("/api/clan/cursor/{cursor}/{maxCursor}/{maxCursor}/0/1"
                + "?minAvgRating={min}&maxAvgRating={max}",
                cursor, max, max, 10, 19
            )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        assertEquals(10, filteredByAvgRatingResult.getResult().size());
        for(int i = 0; i < filteredByAvgRatingResult.getResult().size(); i++)
            assertEquals(18 - i, filteredByAvgRatingResult.getResult().get(i).getId());

        //filtered by all
        PagedSearchResult<List<Clan>> filteredByAllResult = objectMapper.readValue(mvc.perform
        (
            get("/api/clan/cursor/{cursor}/{maxCursor}/{maxCursor}/0/1"
                + "?minActiveMembers={min}&maxActiveMembers={max}"
                + "&minAvgRating={min}&maxAvgRating={max}",
                cursor, max, max, 10, 19, 10, 19
            )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        assertEquals(9, filteredByAllResult.getResult().size());
        for(int i = 0; i < filteredByAllResult.getResult().size(); i++)
            assertEquals(18 - i, filteredByAllResult.getResult().get(i).getId());
    }

}
