// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import com.nephest.battlenet.sc2.config.security.WithBlizzardMockUser;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.service.EventService;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class MatchServiceIT
{

    private static final Logger LOG = LoggerFactory.getLogger(MatchServiceIT.class);

    @Autowired
    private MatchService matchService;

    @Autowired
    private EventService eventService;

    @Autowired
    private BlizzardSC2API api;

    @Autowired
    private GlobalContext globalContext;

    @BeforeAll
    public static void beforeAll(@Autowired BlizzardSC2API api, @Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
    }

    @BeforeEach
    public void beforeEach()
    {
        matchService.getUpdateMatchesTask()
            .setValue(SC2Pulse.instant().minus(MatchService.MATCH_UPDATE_FRAME));
        matchService.setUpdateContext(null);
    }

    @AfterAll
    public static void afterAll(@Autowired BlizzardSC2API api, @Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    public void testAutoRegionRedirect()
    throws ExecutionException, InterruptedException
    {
        api.setAutoForceRegion(true);
        CompletableFuture<MatchUpdateContext> update = new CompletableFuture<>();
        eventService.getMatchUpdateEvent().subscribe(update::complete);
        try
        {
            //no matches found
            eventService.createLadderUpdateEvent(LadderUpdateData.EMPTY);
            update.get();
            assertEquals(Region.KR, api.getForceRegion(Region.EU));
        }
        finally
        {
            for(Region region : globalContext.getActiveRegions()) api.setForceRegion(region, null);
            api.setAutoForceRegion(false);
        }
    }

    @Test
    @WithBlizzardMockUser(partition =  Partition.GLOBAL, username = "user", roles = {SC2PulseAuthority.USER, SC2PulseAuthority.ADMIN})
    public void testWebRegion(@Autowired WebApplicationContext webApplicationContext)
    throws Exception
    {
        MockMvc mvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .alwaysDo(print())
            .build();

        mvc.perform
        (
            post("/admin/blizzard/api/match/web/region/EU")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
        )
            .andExpect(status().isOk())
            .andReturn();

        assertEquals(Set.of(Region.EU), matchService.getWebRegions());

        mvc.perform
        (
            post("/admin/blizzard/api/match/web/region/KR")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
        )
            .andExpect(status().isOk())
            .andReturn();

        assertEquals(Set.of(Region.EU, Region.KR), matchService.getWebRegions());

        mvc.perform
        (
            delete("/admin/blizzard/api/match/web/region/KR")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
        )
            .andExpect(status().isOk())
            .andReturn();

        assertEquals(Set.of(Region.EU), matchService.getWebRegions());
    }

}
