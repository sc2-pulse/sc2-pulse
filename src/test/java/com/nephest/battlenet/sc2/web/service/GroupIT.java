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
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.ProPlayerAccount;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerAccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerDAO;
import com.nephest.battlenet.sc2.model.local.inner.Group;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderPlayerSearchStats;
import com.nephest.battlenet.sc2.model.local.ladder.LadderProPlayer;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderProPlayerDAO;
import com.nephest.battlenet.sc2.web.controller.GroupController;
import com.nephest.battlenet.sc2.web.controller.group.CharacterGroupArgumentResolver;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
    private ProPlayerDAO proPlayerDAO;

    @Autowired
    private ProPlayerAccountDAO proPlayerAccountDAO;

    @Autowired
    private LadderProPlayerDAO ladderProPlayerDAO;

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
                .queryParam
                (
                    "proPlayerId",
                    String.valueOf(initGroup.getProPlayers().get(0).getProPlayer().getId()),
                    String.valueOf(initGroup.getProPlayers().get(1).getProPlayer().getId())
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

        assertEquals(2, result.getProPlayers().size());
        result.getProPlayers().sort(Comparator.comparing(lpp->lpp.getProPlayer().getId()));
        Assertions.assertThat(result.getProPlayers().get(0))
            .usingRecursiveComparison().isEqualTo(initGroup.getProPlayers().get(0));
        Assertions.assertThat(result.getProPlayers().get(1))
            .usingRecursiveComparison().isEqualTo(initGroup.getProPlayers().get(1));
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
            .queryParam
            (
                "proPlayerId",
                String.valueOf(group.getProPlayers().get(0).getProPlayer().getId())
            )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), Long[].class);
        Arrays.sort(result);
        Long[] expectedResult = new Long[]{1L, 2L, 11L, 12L, 20L};
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
        Clan[] clans = clanDAO.merge(new LinkedHashSet<>(List.of(
            new Clan(null, "clan1", Region.EU, "clanName1"),
            new Clan(null, "clan2", Region.EU, "clanName2"),
            new Clan(null, "clan3", Region.EU, "clanName3")
        )))
            .toArray(Clan[]::new);
        clanMemberDAO.merge(Set.of(
            new ClanMember(1L, clans[0].getId()),
            new ClanMember(2L, clans[0].getId()),
            new ClanMember(3L, clans[1].getId())
        ));

        LocalDate bd1 = LocalDate.now().minusYears(20);
        OffsetDateTime odt = OffsetDateTime.now();
        ProPlayer[] proPlayers = new ProPlayer[]
        {
            new ProPlayer(null, 1L, "tag1", "name1", "US", bd1, 1, odt, 1),
            new ProPlayer(null, 2L, "tag2", "name2", "US", bd1.minusDays(2), 2, odt, 2),
            new ProPlayer(null, 3L, "tag3", "name3", "US", bd1.minusDays(3), 3, odt, 3)
        };
        for(ProPlayer proPlayer : proPlayers) proPlayerDAO.merge(proPlayer);
        proPlayerAccountDAO.merge(Set.of(
            new ProPlayerAccount(proPlayers[0].getId(), 11L),
            new ProPlayerAccount(proPlayers[0].getId(), 12L),
            new ProPlayerAccount(proPlayers[1].getId(), 13L)
        ));
        List<LadderProPlayer> ladderProPlayers = ladderProPlayerDAO.findByIds(Arrays
            .stream(proPlayers)
            .map(ProPlayer::getId)
            .collect(Collectors.toSet()));

        return new Group(List.of(), Arrays.asList(clans), ladderProPlayers);
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
        Clan clan = clanDAO.merge(Set.of(new Clan(null, "clan1", Region.EU, "clanName1")))
            .iterator().next();
        Set<ClanMember> members = LongStream
            .range(1, CharacterGroupArgumentResolver.CHARACTERS_MAX + 2)
            .boxed()
            .map(i->new ClanMember(i, clan.getId()))
            .collect(Collectors.toSet());
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

    @Test
    public void whenClanHistoryPageSizeIsExceeded_thenBadRequest()
    throws Exception
    {
        mvc.perform(get("/api/group/clan/history")
                .queryParam("characterId", "1")
                .queryParam("limit", String.valueOf(GroupController.CLAN_MEMBER_EVENT_PAGE_SIZE_MAX + 1)))
            .andExpect(status().isBadRequest());
    }

    @Test
    public void whenProPlayerSizeIsExceeded_thenBadRequest()
    throws Exception
    {
        String[] longIdList = LongStream.range(0, CharacterGroupArgumentResolver.PRO_PLAYERS_MAX + 1)
            .boxed()
            .map(String::valueOf)
            .toArray(String[]::new);
        mvc.perform(get("/api/group/flat").queryParam("proPlayerId", longIdList))
            .andExpect(status().isBadRequest());
    }


}
