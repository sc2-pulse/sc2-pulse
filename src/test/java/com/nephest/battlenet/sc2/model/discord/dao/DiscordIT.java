// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.discord.dao;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.config.security.WithBlizzardMockUser;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.discord.DiscordIdentity;
import com.nephest.battlenet.sc2.model.discord.DiscordUser;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterStatsDAO;
import com.nephest.battlenet.sc2.model.local.ladder.common.CommonCharacter;
import com.nephest.battlenet.sc2.web.service.DiscordService;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
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
public class DiscordIT
{

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private PlayerCharacterDAO playerCharacterDAO;

    @Autowired
    private DiscordUserDAO discordUserDAO;

    @Autowired
    private PlayerCharacterStatsDAO playerCharacterStatsDAO;

    @Autowired
    private DiscordService discordService;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mvc;

    public static final String BATTLE_TAG = "battletag#0";

    @BeforeEach
    public void beforeEach
    (
        @Autowired DataSource dataSource,
        @Autowired WebApplicationContext webApplicationContext,
        @Autowired CacheManager cacheManager
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
            cacheManager.getCacheNames()
                .forEach(cacheName->cacheManager.getCache(cacheName).clear());
        }

        mvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .alwaysDo(print())
            .build();
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
    @WithBlizzardMockUser(partition = Partition.GLOBAL, username = BATTLE_TAG)
    public void testChain()
    throws Exception
    {
        seasonGenerator.generateDefaultSeason
        (
            List.of(Region.EU),
            List.of(BaseLeague.LeagueType.values()),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            1
        );
        playerCharacterStatsDAO.mergeCalculate();
        DiscordUser discordUser = new DiscordUser(123L, "name", 123);

        discordService.linkAccountToNewDiscordUser(2L, discordUser);
        verifyLinkedDiscordUser(1L, null);
        verifyLinkedDiscordUser(2L, discordUser);

        //previous link is removed, one-to-one relationship
        discordService.linkAccountToDiscordUser(1L, discordUser.getId());
        verifyLinkedDiscordUser(1L, discordUser);
        verifyLinkedDiscordUser(2L, null);

        //second links is removed, no linked chars
        mvc.perform
        (
            post("/api/my/discord/unlink")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf().asHeader())
        )
            .andExpect(status().isOk());
        verifyLinkedDiscordUser(1L, null);
        verifyLinkedDiscordUser(2L, null);
    }

    private void verifyLinkedDiscordUser(Long characterId, DiscordUser discordUser)
    throws Exception
    {
        CommonCharacter commonChar = objectMapper.readValue(mvc.perform
            (
                get("/api/character/{id}/common", characterId)
                    .contentType(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), CommonCharacter.class);
        if(discordUser == null)
        {
            assertNull(commonChar.getDiscordUser());
        }
        else
        {
            assertDeepEquals(discordUser, commonChar.getDiscordUser());
        }
    }

    @Test
    public void testUpdateDiscordUser()
    {
        DiscordUser discordUser =
            discordUserDAO.merge(new DiscordUser(123L, "name123", 123))[0];
        DiscordUser foundDiscordUser = discordUserDAO.find(123L).get(0);
        assertEquals(discordUser.getId(), foundDiscordUser.getId());
        assertEquals(discordUser.getName(), foundDiscordUser.getName());
        assertEquals(discordUser.getDiscriminator(), foundDiscordUser.getDiscriminator());

        discordUserDAO.merge
        (
            new DiscordUser(123L, "name321", 321),
            //new
            new DiscordUser(456L, "name456", 456),
            //new, duplicate, no exception is expected
            new DiscordUser(567L, "name567", 567),
            new DiscordUser(567L, "name567", 567)
        );

        DiscordUser updatedUser = discordUserDAO.find(123L).get(0);
        assertEquals(123L, updatedUser.getId());
        assertEquals("name321", updatedUser.getName());
        assertEquals(321, updatedUser.getDiscriminator());

        verifyStdDiscordUser(discordUserDAO.find(567L).get(0), 567);
    }

    @Test
    public void testRemoveEmptyDiscordUsers()
    {
        Account acc1 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        Account acc3 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#3"));

        DiscordUser[] users = discordUserDAO.merge
        (
            new DiscordUser(1L, "name1", 1),
            new DiscordUser(2L, "name2", 2),
            new DiscordUser(3L, "name3", 3),
            new DiscordUser(4L, "name4", 4)
        );

        discordService.linkAccountToDiscordUser(acc1.getId(), users[0].getId());
        discordService.linkAccountToDiscordUser(acc3.getId(), users[2].getId());

        assertEquals(2, discordUserDAO.removeUsersWithNoAccountLinked());
        List<DiscordUser> foundUsers = discordUserDAO.findByIdCursor(0L, 10);
        foundUsers.sort(Comparator.comparing(DiscordUser::getId));
        assertEquals(2, foundUsers.size());
        verifyStdDiscordUser(foundUsers.get(0), 1);
        verifyStdDiscordUser(foundUsers.get(1), 3);
    }

    @Test
    public void testFindByDiscordUserId()
    {
        Account acc1 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#1"));
        Account acc2 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag#2"));

        DiscordUser[] users = discordUserDAO.merge
        (
            new DiscordUser(1L, "name1", 1),
            new DiscordUser(2L, "name2", 2)
        );

        discordService.linkAccountToDiscordUser(acc1.getId(), users[0].getId());

        assertEquals(acc1, accountDAO.findByDiscordUserId(1L).get());
        assertTrue(accountDAO.findByDiscordUserId(2L).isEmpty());
    }

    private static void assertDeepEquals(DiscordIdentity user1, DiscordIdentity user2)
    {
        assertNotNull(user1);
        assertNotNull(user2);
        assertEquals(user1.getName(), user2.getName());
        assertEquals(user1.getDiscriminator(), user2.getDiscriminator());
    }

    public static void verifyStdDiscordUser(DiscordUser user, int base)
    {
        assertEquals(base, user.getId());
        assertEquals("name" + base, user.getName());
        assertEquals(base, user.getDiscriminator());
    }

}
