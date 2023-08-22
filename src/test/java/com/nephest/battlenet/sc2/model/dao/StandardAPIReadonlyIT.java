// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.LeagueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.PopulationStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchIndependentIT;
import com.nephest.battlenet.sc2.web.controller.CharacterController;
import com.nephest.battlenet.sc2.web.service.StatsService;
import com.nephest.battlenet.sc2.web.service.WebServiceTestUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = AllTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class StandardAPIReadonlyIT
{

    public static final int TEAMS_PER_LEAGUE = 2;

    @Autowired
    private ObjectMapper objectMapper;

    private static MockMvc mvc;


    @BeforeAll
    public static void init
    (
        @Autowired DataSource dataSource,
        @Autowired WebApplicationContext webApplicationContext,
        @Autowired SeasonGenerator generator,
        @Autowired TeamDAO teamDAO,
        @Autowired LeagueStatsDAO leagueStatsDAO,
        @Autowired PopulationStateDAO populationStateDAO,
        @Autowired PlayerCharacterStatsDAO playerCharacterStatsDAO
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }

        generator.generateDefaultSeason
        (
            List.of(Region.values()),
            List.of(BaseLeague.LeagueType.values()),
            new ArrayList<>(QueueType.getTypes(StatsService.VERSION)),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            TEAMS_PER_LEAGUE
        );
        teamDAO.updateRanks(SeasonGenerator.DEFAULT_SEASON_ID);
        leagueStatsDAO.calculateForSeason(SeasonGenerator.DEFAULT_SEASON_ID);
        populationStateDAO.takeSnapshot(List.of(SeasonGenerator.DEFAULT_SEASON_ID));
        playerCharacterStatsDAO.mergeCalculate();

        mvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .alwaysDo(print())
            .build();
    }

    @Test
    public void testFindCharacterById() throws Exception
    {
        PlayerCharacter[] characters = WebServiceTestUtil
            .getObject(mvc, objectMapper, new TypeReference<>(){}, "/api/character/1,2");
        Arrays.sort(characters, Comparator.comparing(PlayerCharacter::getId));

        assertEquals(2, characters.length);

        PlayerCharacter char1 = characters[0];
        assertEquals(1, char1.getId());
        assertEquals("character#0", char1.getName());

        PlayerCharacter char2 = characters[1];
        assertEquals(2, char2.getId());
        assertEquals("character#10", char2.getName());
    }

    @Test
    public void testFindCharacterByIdsLongList() throws Exception
    {
        String ids = LongStream.range(0, CharacterController.PLAYER_CHARACTERS_MAX + 1)
            .boxed()
            .map(String::valueOf)
            .collect(Collectors.joining(","));

        mvc.perform
        (
            get("/api/character/{ids}", ids)
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isBadRequest());
    }

    @Test
    public void testFindFullCharactersByIds() throws Exception
    {
        LadderDistinctCharacter[] characters = WebServiceTestUtil.getObject
        (
            mvc,
            objectMapper,
            new TypeReference<>(){},
            "/api/group/character/full?characterId=1,2"
        );
        Arrays.sort(characters, Comparator.comparing(c->c.getMembers().getCharacter().getId()));

        assertEquals(2, characters.length);

        LadderSearchIndependentIT.verify
        (
            characters[0],
            1L, Partition.GLOBAL, "battletag#0",
            1L, Region.US, 1, 0L, "character#0",
            null, null,
            0, 3, BaseLeague.LeagueType.BRONZE,
            0, 3, 56,
            null, null, null
        );
        LadderSearchIndependentIT.verify
        (
            characters[1],
            2L, Partition.GLOBAL, "battletag#10",
            2L, Region.US, 1, 10L, "character#10",
            null, null,
            1, 6, BaseLeague.LeagueType.BRONZE,
            1, 6, 55,
            null, null, null
        );
    }

    @Test
    public void whenNoCharacter_thenReturn404()
    throws Exception
    {
        mvc.perform
        (
            get("/api/character/{id}/common", 12345678)
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isNotFound());
    }

}
