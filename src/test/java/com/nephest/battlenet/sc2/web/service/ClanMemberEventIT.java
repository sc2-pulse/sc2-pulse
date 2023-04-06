// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static com.nephest.battlenet.sc2.model.local.ClanMemberEvent.EventType.JOIN;
import static com.nephest.battlenet.sc2.model.local.ClanMemberEvent.EventType.LEAVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.ClanMember;
import com.nephest.battlenet.sc2.model.local.ClanMemberEvent;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanMemberEventDAO;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = {AllTestConfig.class})
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class ClanMemberEventIT
{

    @Autowired
    private ClanDAO clanDAO;

    @Autowired
    private ClanMemberDAO clanMemberDAO;

    @Autowired
    private ClanMemberEventDAO clanMemberEventDAO;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mvc;

    private PlayerCharacter[] characters;
    private Clan[] clans;

    @BeforeEach
    public void beforeEach(@Autowired DataSource dataSource)
    throws SQLException
    {
        try (Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        cacheManager.getCacheNames().forEach(cacheName -> cacheManager.getCache(cacheName).clear());

        Account[] accounts = seasonGenerator.generateAccounts(Partition.GLOBAL, "acc", 10);
        characters = seasonGenerator.generateCharacters("name", accounts, Region.EU, 100L);
        clans = clanDAO.merge
        (
            new Clan(null, "tag1", Region.EU, "name1"),
            new Clan(null, "tag2", Region.EU, "name2"),
            new Clan(null, "tag3", Region.EU, "name3"),
            new Clan(null, "tag4", Region.EU, "name4"),
            new Clan(null, "tag5", Region.EU, "name5")
        );

        clanMemberDAO.merge
        (
            new ClanMember(characters[0].getId(), clans[0].getId()),
            new ClanMember(characters[1].getId(), clans[0].getId()),
            new ClanMember(characters[2].getId(), clans[1].getId()),
            new ClanMember(characters[3].getId(), clans[2].getId()),
            new ClanMember(characters[4].getId(), clans[3].getId()),
            new ClanMember(characters[5].getId(), clans[4].getId())
        );
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
    public void testChain() throws Exception
    {
        OffsetDateTime odt1 = OffsetDateTime.now().minusDays(1);
        assertEquals(6, clanMemberEventDAO.merge
        (
            new ClanMemberEvent(characters[0].getId(), clans[0].getId(), JOIN, odt1),
            new ClanMemberEvent(characters[1].getId(), clans[0].getId(), JOIN, odt1),
            new ClanMemberEvent(characters[2].getId(), clans[1].getId(), JOIN, odt1),
            new ClanMemberEvent(characters[3].getId(), clans[2].getId(), JOIN, odt1),
            new ClanMemberEvent(characters[4].getId(), clans[3].getId(), JOIN, odt1),
            new ClanMemberEvent(characters[5].getId(), clans[4].getId(), JOIN, odt1),
            //ignored, player is not in a clan, nothing has changed
            new ClanMemberEvent(characters[6].getId(), null, LEAVE, odt1)
        ));

        int secondsSincePrevious = 10;
        OffsetDateTime odt2 = odt1.plusSeconds(secondsSincePrevious);
        clanMemberEventDAO.merge
        (
            new ClanMemberEvent(characters[0].getId(), null, LEAVE, odt2),
            //inject "left" evt for clan[0]
            new ClanMemberEvent(characters[1].getId(), clans[1].getId(), JOIN, odt2),
            //ignored, nothing has changed
            new ClanMemberEvent(characters[2].getId(), clans[1].getId(), JOIN, odt2)
        );

        ClanMemberEvent[] evts1 = objectMapper.readValue(mvc.perform
        (
            get("/api/group/clan/history")
                .queryParam
                (
                    "characterId",
                    String.valueOf(characters[3].getId()),
                    String.valueOf(characters[4].getId())
                )
                .queryParam
                (
                    "clanId",
                    String.valueOf(clans[0].getId()),
                    String.valueOf(clans[1].getId()),
                    String.valueOf(clans[2].getId())
                )
                .queryParam("limit", "3")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), ClanMemberEvent[].class);
        assertEquals(3, evts1.length);
        verifyEvent
        (
            evts1[0],
            characters[1].getId(),
            clans[1].getId(),
            JOIN,
            odt2,
            0
        );
        verifyEvent
        (
            evts1[1],
            characters[0].getId(),
            clans[0].getId(),
            LEAVE,
            odt2,
            secondsSincePrevious
        );
        verifyEvent
        (
            evts1[2],
            characters[1].getId(),
            clans[0].getId(),
            LEAVE,
            odt2.minus(Duration.ofMillis(1)),
            secondsSincePrevious
        );

        //next page
        ClanMemberEvent[] evts2 = objectMapper.readValue(mvc.perform
        (
            get("/api/group/clan/history")
                .queryParam
                (
                    "characterId",
                    String.valueOf(characters[3].getId()),
                    String.valueOf(characters[4].getId())
                )
                .queryParam
                (
                    "clanId",
                    String.valueOf(clans[0].getId()),
                    String.valueOf(clans[1].getId()),
                    String.valueOf(clans[2].getId())
                )
                .queryParam
                (
                    "characterIdCursor",
                    String.valueOf(evts1[2].getPlayerCharacterId())
                )
                .queryParam
                (
                    "createdCursor",
                    evts1[2].getCreated().toString()
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), ClanMemberEvent[].class);
        assertEquals(5, evts2.length);
        verifyEvent
        (
            evts2[0],
            characters[4].getId(),
            clans[3].getId(),
            JOIN,
            odt1,
            null
        );
        verifyEvent
        (
            evts2[1],
            characters[3].getId(),
            clans[2].getId(),
            JOIN,
            odt1,
            null
        );
        verifyEvent
        (
            evts2[2],
            characters[2].getId(),
            clans[1].getId(),
            JOIN,
            odt1,
            null
        );
        verifyEvent
        (
            evts2[3],
            characters[1].getId(),
            clans[0].getId(),
            JOIN,
            odt1,
            null
        );
        verifyEvent
        (
            evts2[4],
            characters[0].getId(),
            clans[0].getId(),
            JOIN,
            odt1,
            null
        );

        //next page, empty
        mvc.perform
        (
            get("/api/group/clan/history")
                .queryParam
                (
                    "characterId",
                    String.valueOf(characters[3].getId()),
                    String.valueOf(characters[4].getId())
                )
                .queryParam
                (
                    "clanId",
                    String.valueOf(clans[0].getId()),
                    String.valueOf(clans[1].getId()),
                    String.valueOf(clans[2].getId())
                )
                .queryParam
                (
                    "characterIdCursor",
                    String.valueOf(evts2[4].getPlayerCharacterId())
                )
                .queryParam
                (
                    "createdCursor",
                    evts2[4].getCreated().toString()
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNotFound())
            .andExpect(content().string(""));
    }

    public static void verifyEvent
    (
        ClanMemberEvent evt,
        Long characterId,
        Integer clanId,
        ClanMemberEvent.EventType type,
        OffsetDateTime created,
        Integer secondsSincePrevious
    )
    {
        assertEquals(characterId, evt.getPlayerCharacterId());
        assertEquals(clanId, evt.getClanId());
        assertEquals(type, evt.getType());
        if(created != null) assertTrue(created.isEqual(evt.getCreated()));
        assertEquals(secondsSincePrevious, evt.getSecondsSincePrevious());
    }

}
