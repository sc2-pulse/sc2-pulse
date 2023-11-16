// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import com.nephest.battlenet.sc2.config.security.WithBlizzardMockUser;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardAccount;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardPlayerCharacter;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardSeason;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTeam;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTeamMember;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTeamMemberRace;
import com.nephest.battlenet.sc2.model.local.Division;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.LeagueTier;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.Validator;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = {AllTestConfig.class})
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class StatsServiceIT
{

    private StatsService statsService;
    private TeamDAO teamDAO;

    @Autowired
    private StatsService realStatsService;

    @Autowired
    private BlizzardSC2API api;

    @Autowired @Qualifier("dbExecutorService")
    private ExecutorService dbExecutorService;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    public void beforeEach
    (
        @Autowired DataSource dataSource,
        @Autowired WebApplicationContext webApplicationContext,
        @Autowired Validator validator
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        teamDAO = mock(TeamDAO.class);
        statsService = new StatsService
        (
            null,
            null,
            mock(SeasonDAO.class),
            null,
            null,
            null,
            teamDAO,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            validator,
            dbExecutorService,
            null,
            null
        );
        StatsService nss = mock(StatsService.class);
        statsService.setNestedService(nss);
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
    public void testInvalidTeam()
    {
        BlizzardTeam noBattletagTeam = new BlizzardTeam();
        BlizzardAccount account = new BlizzardAccount(1L, null); //null battletag
        BlizzardPlayerCharacter character = new BlizzardPlayerCharacter(1L, 1, "name#123");
        noBattletagTeam.setWins(1);
        noBattletagTeam.setMembers(new BlizzardTeamMember[]{new BlizzardTeamMember(
            character,
            new BlizzardTeamMemberRace[]{new BlizzardTeamMemberRace(Race.PROTOSS, 1)},
            account)
        });

        statsService.updateTeams(new BlizzardTeam[]{noBattletagTeam}, mock(Season.class),
            new League(1, 1, BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED),
            mock(LeagueTier.class), mock(Division.class));

        verify(teamDAO, never()).merge(any());
    }

    @Test
    public void testMaxLadderId()
    {
        Region region = Region.US;
        BlizzardSeason bSeason = api.getSeason(region, 44).block();
        assertEquals(292761, realStatsService.getMaxLadderId(bSeason, region));
    }

    @Test
    @WithBlizzardMockUser(partition =  Partition.GLOBAL, username = "user", roles = {SC2PulseAuthority.USER, SC2PulseAuthority.ADMIN})
    public void testSetPartialUpdate()
    throws Exception
    {
        for(Region region : Region.values()) assertFalse(realStatsService.isPartialUpdate(region));

        mvc.perform
        (
            post("/admin/update/partial/EU")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
            .andExpect(status().isOk())
            .andReturn();
        for(Region region : Region.values())
            assertEquals(region == Region.EU, realStatsService.isPartialUpdate(region));

        mvc.perform
        (
            delete("/admin/update/partial/EU")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
            .andExpect(status().isOk())
            .andReturn();
        for(Region region : Region.values()) assertFalse(realStatsService.isPartialUpdate(region));
    }

}
