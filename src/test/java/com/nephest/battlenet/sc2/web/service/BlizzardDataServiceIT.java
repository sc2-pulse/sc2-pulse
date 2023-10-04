// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.security.WithBlizzardMockUser;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardFullPlayerCharacter;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.web.controller.BlizzardDataController;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class BlizzardDataServiceIT
{

    @Autowired
    private SeasonDAO seasonDAO;

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private PlayerCharacterDAO playerCharacterDAO;

    @Autowired
    private BlizzardSC2API api;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate template;

    @Autowired
    private BlizzardDataController controller;

    private MockMvc mvc;

    private Account acc1, acc2;
    private PlayerCharacter char1_1, char1_2, char2_1;
    private final List<BlizzardFullPlayerCharacter> bCharacters = List.of
    (
        new BlizzardFullPlayerCharacter(1L, 1, "name#1", Region.US),
        new BlizzardFullPlayerCharacter(2L, 2, "name#10", Region.EU),
        new BlizzardFullPlayerCharacter(4L, 4, "name#4", Region.KR)
    );

    @BeforeEach
    public void beforeEach
    (
        @Autowired DataSource dataSource,
        @Autowired WebApplicationContext webApplicationContext
    ) throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        mvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .alwaysDo(print())
            .build();

        seasonDAO.merge
        (
            new Season
            (
                null, 25,
                Region.EU,
                2020, 1,
                LocalDate.now(), LocalDate.now().plusMonths(3)
            )
        );
        acc1 = accountDAO.merge(new Account(null, Partition.GLOBAL, "user#1"));
        char1_1 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc1.getId(), Region.US, 1L, 1, "name#1"));
        char1_2 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc1.getId(), Region.EU, 2L, 2, "name#2"));

        acc2 = accountDAO.merge(new Account(null, Partition.GLOBAL, "user#2"));
        char2_1 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc2.getId(), Region.EU, 3L, 3, "name#3"));
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
    @WithBlizzardMockUser(partition =  Partition.GLOBAL, username = "user#22")
    public void testImportData() throws Exception
    {
        accountDAO.updateAnonymousFlag(acc1.getId(), true);
        playerCharacterDAO.updateAnonymousFlag(char1_1.getId(), true);
        playerCharacterDAO.updateAnonymousFlag(char1_2.getId(), true);
        OffsetDateTime updatedMin = OffsetDateTime.now()
            .plus(BlizzardDataService.ACCOUNT_IMPORT_DURATION);

        try(MockWebServer server = new MockWebServer())
        {
            server.start();
            api.setRegionUri(server.url("/someurl").uri().toString());
            server.enqueue(new MockResponse().setBody(objectMapper.writeValueAsString(bCharacters)));
            mvc.perform
            (
                post("/data/battle-net")
                    .param("action", BlizzardDataController.Action.IMPORT.toString())
                    .with(csrf().asHeader())
            )
                .andExpect(status().isOk());
        }
        finally
        {
            api.setRegionUri(null);
        }
        controller.waitForLastAction();

        Account foundAcc1 = accountDAO.findByIds(Set.of(acc1.getId())).get(0);
        assertEquals("user#22", foundAcc1.getBattleTag());
        assertFalse(accountDAO.getAnonymousFlag(acc1.getId()));
        assertTrue(updatedMin.isBefore(accountDAO.getUpdated(acc1.getId())));

        PlayerCharacter newChar = playerCharacterDAO.find(Region.KR, 4, 4L).orElseThrow();
        verifyCharacter(char1_1.getId(), acc1.getId(), "name#1", false);
        verifyCharacter(char1_2.getId(), acc1.getId(), "name#10", false);
        verifyCharacter(newChar.getId(), acc1.getId(), "name#4", false);

        verifyOtherDataWasNotChanged();
    }

    @Test
    @WithBlizzardMockUser(partition =  Partition.GLOBAL, username = "user#22")
    public void testDeleteData() throws Exception
    {
        try(MockWebServer server = new MockWebServer())
        {
            server.start();
            api.setRegionUri(server.url("/someurl").uri().toString());
            server.enqueue(new MockResponse().setBody(objectMapper.writeValueAsString(bCharacters)));
            mvc.perform
            (
                post("/data/battle-net")
                    .param("action", BlizzardDataController.Action.DELETE.toString())
                    .with(csrf().asHeader())
            )
                .andExpect(status().isOk());
        }
        finally
        {
            api.setRegionUri(null);
        }
        controller.waitForLastAction();
        OffsetDateTime updatedMax = OffsetDateTime.now()
            .minus(BlizzardPrivacyService.DATA_TTL);

        Account foundAcc1 = accountDAO.findByIds(Set.of(acc1.getId())).get(0);
        assertEquals("user#1", foundAcc1.getBattleTag());
        assertTrue(accountDAO.getAnonymousFlag(acc1.getId()));
        assertTrue(updatedMax.isAfter(accountDAO.getUpdated(acc1.getId())));

        verifyCharacter(char1_1.getId(), acc1.getId(), "name#1", true, updatedMax);
        verifyCharacter(char1_2.getId(), acc1.getId(), "name#2", true, updatedMax);
        assertTrue(playerCharacterDAO.find(Region.KR, 4, 4).isEmpty());

        verifyOtherDataWasNotChanged();
    }

    private void verifyCharacter
    (long id, long accountId, String name, boolean anonymous, OffsetDateTime updatedMax)
    {
        PlayerCharacter foundChar = playerCharacterDAO.find(Set.of(id)).get(0);
        assertEquals(accountId, foundChar.getAccountId());
        assertEquals(name, foundChar.getName());
        assertEquals(anonymous, playerCharacterDAO.getAnonymousFlag(id));
        if(updatedMax != null)
            assertTrue(updatedMax.isAfter(playerCharacterDAO.getUpdated(id)));
    }

    private void verifyCharacter(long id, long accountId, String name, boolean anonymous)
    {
        verifyCharacter(id, accountId, name, anonymous, null);
    }

    private void verifyOtherDataWasNotChanged()
    {
        Account foundAcc2 = accountDAO.findByIds(Set.of(acc2.getId())).get(0);
        assertEquals("user#2", foundAcc2.getBattleTag());
        verifyCharacter(char2_1.getId(), acc2.getId(), "name#3", false);
    }

}
