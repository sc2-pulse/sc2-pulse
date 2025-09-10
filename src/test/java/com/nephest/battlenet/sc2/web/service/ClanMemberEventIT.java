// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static com.nephest.battlenet.sc2.model.local.ClanMemberEvent.EventType.JOIN;
import static com.nephest.battlenet.sc2.model.local.ClanMemberEvent.EventType.LEAVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
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
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterStatsDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderClanMemberEvents;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderPlayerSearchStats;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.model.validation.CursorNavigableResult;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.ConversionService;
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
    private PlayerCharacterStatsDAO playerCharacterStatsDAO;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired @Qualifier("mvcConversionService")
    private ConversionService mvcConversionService;

    @Autowired
    private MockMvc mvc;

    private Account[] accounts;
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

        accounts = seasonGenerator.generateAccounts(Partition.GLOBAL, "acc", 10);
        characters = seasonGenerator.generateCharacters("name", accounts, Region.EU, 100L);
        seasonGenerator.generateDefaultSeason(0);
        seasonGenerator.createTeams(characters);
        playerCharacterStatsDAO.mergeCalculate();
        clans = clanDAO.merge(new LinkedHashSet<>(List.of(
            new Clan(null, "tag1", Region.EU, "name1"),
            new Clan(null, "tag2", Region.EU, "name2"),
            new Clan(null, "tag3", Region.EU, "name3"),
            new Clan(null, "tag4", Region.EU, "name4"),
            new Clan(null, "tag5", Region.EU, "name5")
        )))
            .toArray(Clan[]::new);

        clanMemberDAO.merge(Set.of(
            new ClanMember(characters[0].getId(), clans[0].getId()),
            new ClanMember(characters[1].getId(), clans[0].getId()),
            new ClanMember(characters[2].getId(), clans[1].getId()),
            new ClanMember(characters[3].getId(), clans[2].getId()),
            new ClanMember(characters[4].getId(), clans[3].getId()),
            new ClanMember(characters[5].getId(), clans[4].getId())
        ));
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
        OffsetDateTime odt1 = SC2Pulse.offsetDateTime().minusDays(1)
            .withOffsetSameInstant(ZoneOffset.UTC);
        assertEquals(6, clanMemberEventDAO.merge(Set.of(
            new ClanMemberEvent(characters[0].getId(), clans[0].getId(), JOIN, odt1),
            new ClanMemberEvent(characters[1].getId(), clans[0].getId(), JOIN, odt1),
            new ClanMemberEvent(characters[2].getId(), clans[1].getId(), JOIN, odt1),
            new ClanMemberEvent(characters[3].getId(), clans[2].getId(), JOIN, odt1),
            new ClanMemberEvent(characters[4].getId(), clans[3].getId(), JOIN, odt1),
            new ClanMemberEvent(characters[5].getId(), clans[4].getId(), JOIN, odt1),
            //ignored, player is not in a clan, nothing has changed
            new ClanMemberEvent(characters[6].getId(), null, LEAVE, odt1)
        )));

        int secondsSincePrevious = 10;
        OffsetDateTime odt2 = odt1.plusSeconds(secondsSincePrevious)
            .withOffsetSameInstant(ZoneOffset.UTC);
        clanMemberEventDAO.merge(Set.of(
            new ClanMemberEvent(characters[0].getId(), null, LEAVE, odt2),
            //inject "left" evt for clan[0]
            new ClanMemberEvent(characters[1].getId(), clans[1].getId(), JOIN, odt2),
            //ignored, nothing has changed
            new ClanMemberEvent(characters[2].getId(), clans[1].getId(), JOIN, odt2)
        ));

        CursorNavigableResult<LadderClanMemberEvents> evts1 = objectMapper.readValue(mvc.perform
        (
            get("/api/clan-histories")
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
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        evts1.result().getCharacters().sort(Comparator.comparing(
            c->c.getMembers().getCharacter().getId()));
        LadderClanMemberEvents firstEvents = new LadderClanMemberEvents
        (
            List.of
            (
                createCharacter(clans[0], 0),
                createCharacter(clans[0], 1)
            ),
            List.of(clans[0], clans[1]),
            List.of
            (
                new ClanMemberEvent
                (
                    characters[1].getId(),
                    clans[1].getId(),
                    JOIN,
                    odt2,
                    0
                ),
                new ClanMemberEvent
                (
                    characters[0].getId(),
                    clans[0].getId(),
                    LEAVE,
                    odt2,
                    secondsSincePrevious
                ),
                new ClanMemberEvent
                (
                    characters[1].getId(),
                    clans[0].getId(),
                    LEAVE,
                    odt2.minus(Duration.ofMillis(1)),
                    secondsSincePrevious
                )
            )
        );
        Assertions.assertThat(evts1.result())
            .usingRecursiveComparison()
            .isEqualTo(firstEvents);

        //next page
        CursorNavigableResult<LadderClanMemberEvents> evts2 = objectMapper.readValue(mvc.perform
        (
            get("/api/clan-histories")
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
                    "after",
                    mvcConversionService.convert(evts1.navigation().after(), String.class)
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        evts2.result().getCharacters().sort(Comparator.comparing(
            c->c.getMembers().getCharacter().getId()));
        Assertions.assertThat(evts2.result()).usingRecursiveComparison().isEqualTo
        (
            new LadderClanMemberEvents
            (
                List.of
                (
                    createCharacter(clans[0], 0),
                    createCharacter(clans[0], 1),
                    createCharacter(clans[1], 2),
                    createCharacter(clans[2], 3),
                    createCharacter(clans[3], 4)
                ),
                List.of
                (
                    clans[0],
                    clans[1],
                    clans[2],
                    clans[3]
                ),
                List.of
                (
                    new ClanMemberEvent
                    (
                        characters[4].getId(),
                        clans[3].getId(),
                        JOIN,
                        odt1,
                        null
                    ),
                    new ClanMemberEvent
                    (
                        characters[3].getId(),
                        clans[2].getId(),
                        JOIN,
                        odt1,
                        null
                    ),
                    new ClanMemberEvent
                    (
                        characters[2].getId(),
                        clans[1].getId(),
                        JOIN,
                        odt1,
                        null
                    ),
                    new ClanMemberEvent
                    (
                        characters[1].getId(),
                        clans[0].getId(),
                        JOIN,
                        odt1,
                        null
                    ),
                    new ClanMemberEvent
                    (
                        characters[0].getId(),
                        clans[0].getId(),
                        JOIN,
                        odt1,
                        null
                    )
                )
            )
        );

        //next page, empty
        assertNull(evts2.navigation().after());

        //prev page
        CursorNavigableResult<LadderClanMemberEvents> backwardResult = objectMapper.readValue(mvc.perform(
            get("/api/clan-histories")
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
                    "before",
                    mvcConversionService.convert(evts2.navigation().before(), String.class)
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        backwardResult.result().getCharacters().sort(Comparator.comparing(
            c->c.getMembers().getCharacter().getId()));
        Assertions.assertThat(backwardResult.result())
            .usingRecursiveComparison()
            .isEqualTo(firstEvents);
    }

    @Test
    public void whenLeavingClanWithoutBeingInClanButWasInClanPreviously_thenIgnoreEvent() throws Exception
    {
        OffsetDateTime odt1 = SC2Pulse.offsetDateTime().minusDays(1);
        assertEquals(1, clanMemberEventDAO.merge(Set.of(
            new ClanMemberEvent(characters[0].getId(), clans[0].getId(), JOIN, odt1)
        )));

        int secondsSincePrevious = 10;
        OffsetDateTime odt2 = odt1.plusSeconds(secondsSincePrevious);
        clanMemberEventDAO.merge(Set.of(
            new ClanMemberEvent(characters[0].getId(), null, LEAVE, odt2)
        ));

        //should be ignored because player is not in a clan
        int secondsSincePrevious3 = 20;
        OffsetDateTime odt3 = odt2.plusSeconds(secondsSincePrevious3);
        clanMemberEventDAO.merge(Set.of(
            new ClanMemberEvent(characters[0].getId(), null, LEAVE, odt3)
        ));

        CursorNavigableResult<LadderClanMemberEvents> evts = objectMapper.readValue(mvc.perform
        (
            get("/api/clan-histories")
                .queryParam
                (
                    "characterId",
                    String.valueOf(characters[0].getId())
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        Assertions.assertThat(evts.result())
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .isEqualTo
            (
                new LadderClanMemberEvents
                (
                    List.of(createCharacter(clans[0], 0)),
                    List.of(clans[0]),
                    List.of
                    (
                        new ClanMemberEvent
                        (
                            characters[0].getId(),
                            clans[0].getId(),
                            LEAVE,
                            odt2,
                            secondsSincePrevious
                        ),
                        new ClanMemberEvent
                        (
                            characters[0].getId(),
                            clans[0].getId(),
                            JOIN,
                            odt1,
                            null
                        )
                    )
                )
            );
    }

    @Test
    public void whenSeveralEventsWithSameCharacter_thenUseFirstAndIgnoreRest()
    throws Exception
    {
        OffsetDateTime odt1 = SC2Pulse.offsetDateTime().minusDays(1);
        assertEquals(1, clanMemberEventDAO.merge(new LinkedHashSet<>(List.of(
            new ClanMemberEvent(characters[0].getId(), clans[0].getId(), JOIN, odt1),
            new ClanMemberEvent(characters[0].getId(), clans[1].getId(), JOIN, odt1.plusSeconds(1))
        ))));

        CursorNavigableResult<LadderClanMemberEvents> evts = objectMapper.readValue(mvc.perform
        (
            get("/api/clan-histories")
                .queryParam
                (
                    "characterId",
                    String.valueOf(characters[0].getId())
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        Assertions.assertThat(evts.result())
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .isEqualTo
            (
                new LadderClanMemberEvents
                (
                    List.of(createCharacter(clans[0], 0)),
                    List.of(clans[0]),
                    List.of
                    (
                        new ClanMemberEvent
                        (
                            characters[0].getId(),
                            clans[0].getId(),
                            JOIN,
                            odt1,
                            null
                        )
                    )
                )
            );
    }

    @Test
    public void whenSearchingByClanIds_thenDontExpandToCharacterIdsAndSearchByClanIdsInstead()
    throws Exception
    {
        OffsetDateTime odt1 = SC2Pulse.offsetDateTime().minusDays(1);
        OffsetDateTime odt2 = odt1.plusSeconds(20);
        OffsetDateTime odt3 = odt2.plusSeconds(30);
        assertEquals(1, clanMemberEventDAO.merge(Set.of(
            new ClanMemberEvent(characters[0].getId(), clans[0].getId(), JOIN, odt1)
        )));
        clanMemberDAO.merge(Set.of(new ClanMember(characters[0].getId(), clans[1].getId())));
        assertEquals(1, clanMemberEventDAO.merge(Set.of(
            new ClanMemberEvent(characters[0].getId(), null, LEAVE, odt2)
        )));
        assertEquals(1, clanMemberEventDAO.merge(Set.of(
            new ClanMemberEvent(characters[0].getId(), clans[1].getId(), JOIN, odt3)
        )));

        CursorNavigableResult<LadderClanMemberEvents> evts = objectMapper.readValue(mvc.perform
        (
            get("/api/clan-histories")
                .queryParam
                (
                    "clanId",
                    String.valueOf(clans[1].getId())
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        Assertions.assertThat(evts.result())
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .isEqualTo
            (
                new LadderClanMemberEvents
                (
                    List.of(createCharacter(clans[1], 0)),
                    List.of(clans[1]),
                    List.of
                    (
                        new ClanMemberEvent
                        (
                            characters[0].getId(),
                            clans[1].getId(),
                            JOIN,
                            odt3,
                            30
                        )
                    )
                )
            );
    }

    private LadderDistinctCharacter createCharacter(Clan clan, int ix)
    {
        return new LadderDistinctCharacter
        (
            BaseLeague.LeagueType.BRONZE, ix,
            accounts[ix],
            characters[ix],
            clan,
            null, null, null,
            null,
            null, null, null, ix, ix,
            new LadderPlayerSearchStats(null, null, null),
            new LadderPlayerSearchStats(ix, ix, null)
        );
    }

}
