// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.ClanMember;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterStatsDAO;
import com.nephest.battlenet.sc2.model.local.inner.Group;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderPlayerSearchStats;
import com.nephest.battlenet.sc2.web.controller.group.CharacterGroupArgumentResolver;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.LongStream;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
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
public class GroupIT
{

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ClanDAO clanDAO;

    @Autowired
    private ClanMemberDAO clanMemberDAO;

    @Autowired
    private PlayerCharacterStatsDAO playerCharacterStatsDAO;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private JdbcTemplate template;


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
    public void testGetGroup()
    throws Exception
    {
        Group initGroup = init();
        playerCharacterStatsDAO.mergeCalculate();

        Group result = objectMapper.readValue(mvc.perform
        (
            get("/api/group")
                .queryParam
                (
                    "characterId",
                    String.valueOf(1L),
                    String.valueOf(20L)
                )
                .queryParam
                (
                    "clanId",
                    String.valueOf(initGroup.getClans().get(0).getId()),
                    String.valueOf(initGroup.getClans().get(1).getId())
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), Group.class);

        assertEquals(2, result.getCharacters().size());
        result.getCharacters().sort(Comparator.comparing(c->c.getMembers().getCharacter().getId()));
        Assertions.assertThat(result.getCharacters().get(0))
            .usingRecursiveComparison().isEqualTo(new LadderDistinctCharacter(
                BaseLeague.LeagueType.BRONZE, 0,
                new Account(1L, Partition.GLOBAL, "battletag#0"),
                new PlayerCharacter(1L, 1L, Region.EU, 0L, 1, "character#0"),
                initGroup.getClans().get(0),
                null, null,
                null,
                null, null, null, 3, 3,
                new LadderPlayerSearchStats(null, null, null),
                new LadderPlayerSearchStats(0, 3, null)
            ));
        Assertions.assertThat(result.getCharacters().get(1))
            .usingRecursiveComparison().isEqualTo(new LadderDistinctCharacter(
                BaseLeague.LeagueType.BRONZE, 19,
                new Account(20L, Partition.GLOBAL, "battletag#190"),
                new PlayerCharacter(20L, 20L, Region.EU, 190L, 1, "character#190"),
                null,
                null, null,
                null,
                null, null, null, 60, 60,
                new LadderPlayerSearchStats(null, null, null),
                new LadderPlayerSearchStats(19, 60, null)
            ));

        assertEquals(2, result.getClans().size());
        result.getClans().sort(Comparator.comparing(Clan::getId));
        Assertions.assertThat(result.getClans().get(0))
            .usingRecursiveComparison().isEqualTo(initGroup.getClans().get(0));
        Assertions.assertThat(result.getClans().get(1))
            .usingRecursiveComparison().isEqualTo(initGroup.getClans().get(1));
    }


    @Test
    public void testGetCharacterIds() throws Exception
    {
        Group group = init();
        Long[] result = objectMapper.readValue(mvc.perform
        (
            get("/api/group/flat")
            .queryParam
            (
                "characterId",
                String.valueOf(1L),
                String.valueOf(20L)
            )
            .queryParam
            (
                "clanId",
                String.valueOf(group.getClans().get(0).getId())
            )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), Long[].class);
        Arrays.sort(result);
        Long[] expectedResult = new Long[]{1L, 2L, 20L};
        assertArrayEquals(expectedResult, result);
    }

    private Group init()
    {
        seasonGenerator.generateDefaultSeason
        (
            List.of(Region.EU),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            30
        );
        Clan[] clans = clanDAO.merge
        (
            new Clan(null, "clan1", Region.EU, "clanName1"),
            new Clan(null, "clan2", Region.EU, "clanName2"),
            new Clan(null, "clan3", Region.EU, "clanName3")
        );
        clanMemberDAO.merge
        (
            new ClanMember(1L, clans[0].getId()),
            new ClanMember(2L, clans[0].getId()),
            new ClanMember(3L, clans[1].getId())
        );
        return new Group(List.of(), Arrays.asList(clans));
    }

    @Test
    public void whenIdsAreEmpty_thenBadRequest()
    throws Exception
    {
        mvc.perform(get("/api/group/flat"))
            .andExpect(status().isBadRequest());
    }

    @Test
    public void whenCharacterSizeIsExceeded_thenBadRequest()
    throws Exception
    {
        String[] longIdList = LongStream.range(0, CharacterGroupArgumentResolver.CHARACTERS_MAX + 1)
            .boxed()
            .map(String::valueOf)
            .toArray(String[]::new);
        mvc.perform(get("/api/group/flat").queryParam("characterId", longIdList))
            .andExpect(status().isBadRequest());
    }

    @Test
    public void whenFlatCharacterSizeIsExceeded_thenBadRequest()
    throws Exception
    {
        seasonGenerator.generateDefaultSeason
        (
            List.of(Region.EU),
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            CharacterGroupArgumentResolver.CHARACTERS_MAX + 1
        );
        Clan clan = clanDAO.merge(new Clan(null, "clan1", Region.EU, "clanName1"))[0];
        ClanMember[] members = LongStream.range(1, CharacterGroupArgumentResolver.CHARACTERS_MAX + 2)
            .boxed()
            .map(i->new ClanMember(i, clan.getId()))
            .toArray(ClanMember[]::new);
        clanMemberDAO.merge(members);
        mvc.perform(get("/api/group/flat").queryParam("clanId", "1"))
            .andExpect(status().isBadRequest());
    }

    @Test
    public void whenClanSizeIsExceeded_thenBadRequest()
    throws Exception
    {
        String[] longIdList = LongStream.range(0, CharacterGroupArgumentResolver.CLANS_MAX + 1)
            .boxed()
            .map(String::valueOf)
            .toArray(String[]::new);
        mvc.perform(get("/api/group/flat").queryParam("clanId", longIdList))
            .andExpect(status().isBadRequest());
    }


}
