// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.PopulationState;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.TeamMember;
import com.nephest.battlenet.sc2.model.local.dao.LeagueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.PopulationStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamMemberDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.web.controller.group.TeamGroupArgumentResolver;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = AllTestConfig.class)
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class TeamGroupIT
{

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("mvcConversionService")
    private ConversionService conversionService;

    private static final List<LadderTeam> LADDER_TEAMS = new ArrayList<>();

    @BeforeAll
    public static void beforeAll
    (
        @Autowired DataSource dataSource,
        @Autowired TeamDAO teamDAO,
        @Autowired TeamMemberDAO teamMemberDAO,
        @Autowired PopulationStateDAO populationStateDAO,
        @Autowired LeagueStatsDAO leagueStatsDAO,
        @Autowired SeasonGenerator seasonGenerator
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
            init(teamDAO, teamMemberDAO, populationStateDAO, leagueStatsDAO, seasonGenerator);
        }
    }

    private static void init
    (
        TeamDAO teamDAO,
        TeamMemberDAO teamMemberDAO,
        PopulationStateDAO populationStateDAO,
        LeagueStatsDAO leagueStatsDAO,
        SeasonGenerator seasonGenerator
    )
    {
        LocalDate start = LocalDate.now().minusYears(100);
        LocalDate end = start.plusYears(100);
        List<Season> seasons = IntStream.range(0, TeamGroupArgumentResolver.TEAMS_MAX + 1)
            .boxed()
            .map(i->new Season(null, i + 1, Region.EU, 2020, i, start.plusDays(i), end.plusDays(i)))
            .toList();
        seasonGenerator.generateSeason
        (
            seasons,
            List.of(BaseLeague.LeagueType.BRONZE),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            0
        );
        Account[] accs = seasonGenerator.generateAccounts(Partition.GLOBAL, "name", 1);
        PlayerCharacter character = seasonGenerator
            .generateCharacters("name", accs, Region.EU, 1)[0];
        Team[] teams = new Team[seasons.size()];
        TeamMember[] members = new TeamMember[teams.length];
        BaseLeague league = new BaseLeague
        (
            BaseLeague.LeagueType.BRONZE,
            QueueType.LOTV_1V1,
            TeamType.ARRANGED
        );
        List<LadderTeamMember> ladderTeamMembers = List.of(new LadderTeamMember(
            accs[0],
            character,
            null, null, null, null,
            null,
            1, 0, 0, 0
        ));
        PopulationState emptyPopulationState = new PopulationState(null, null, null);

        /*
            Calculate stats for single season because TEAMS_MAX can be very large, so there can be
            many seasons which considerably slows down the test. Calculate stats for single season
            and ensure that the data is returned.
         */
        int statsSeason = 2;
        int statsSeasonIx = statsSeason - 1;

        for(int i = 0; i < teams.length; i++)
        {
            LadderTeam ladderTeam = new LadderTeam
            (
                null,
                i + 1,
                Region.EU,
                league,
                BaseLeagueTier.LeagueTierType.FIRST,
                i <= 1
                    ? BigInteger.ZERO
                    : i <= TeamGroupArgumentResolver.TEAMS_MAX + 1 - 4
                        ? BigInteger.ONE
                        : i == TeamGroupArgumentResolver.TEAMS_MAX + 1 - 3
                            ? BigInteger.TWO
                            : i == TeamGroupArgumentResolver.TEAMS_MAX + 1 - 2
                                ? BigInteger.valueOf(3L)
                                : BigInteger.valueOf(4L),
                i + 1,
                1L, 1, 1, 1, 1,
                null,
                ladderTeamMembers,
                i == statsSeasonIx
                    ? new PopulationState(null, null, 1, 1, 1)
                    : emptyPopulationState
            );
            int rank = i == statsSeasonIx ? 1 : 0;
            ladderTeam.setRegionRank(rank);
            ladderTeam.setGlobalRank(rank);
            ladderTeam.setLeagueRank(rank);
            LADDER_TEAMS.add(ladderTeam);
            teams[i] = ladderTeam;
            members[i] = new TeamMember(i + 1L, character.getId(), 1, 0, 0, 0);
        }

        List<Team> teamList = Arrays.asList(teams);
        List<TeamMember> memberList = Arrays.asList(members);
        int batchSize = 500;
        for(int i = 0; i * batchSize < teamList.size(); i++)
        {
            teamDAO.merge(new LinkedHashSet<>(
                teamList.subList(i * batchSize, Math.min(teamList.size(), (i + 1) * batchSize))));
            teamMemberDAO.merge(new HashSet<>(
                memberList.subList(i * batchSize, Math.min(memberList.size(), (i + 1) * batchSize))));
        }
        teamList.forEach(t->t.setPoints(null));
        leagueStatsDAO.mergeCalculateForSeason(statsSeason);
        populationStateDAO.takeSnapshot(List.of(statsSeason));
        teamDAO.updateRanks(statsSeason);
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
    public void testFlat() throws Exception
    {
        Long[] ids = objectMapper.readValue(mvc.perform(get("/api/team/group/flat")
            .queryParam
            (
                "legacyUid",
                conversionService.convert
                (
                    new TeamLegacyUid
                    (
                        QueueType.LOTV_1V1,
                        Region.EU,
                        BigInteger.ZERO
                    ),
                    String.class
                ),
                conversionService.convert
                (
                    new TeamLegacyUid
                    (
                        QueueType.LOTV_1V1,
                        Region.EU,
                        BigInteger.ONE
                    ),
                    String.class
                )
            )
            .queryParam
            (
                "teamId",
                String.valueOf(TeamGroupArgumentResolver.TEAMS_MAX),
                String.valueOf(TeamGroupArgumentResolver.TEAMS_MAX + 1)
            )
            .queryParam("fromSeason", "2")
            .queryParam("toSeason", String.valueOf(TeamGroupArgumentResolver.TEAMS_MAX + 1)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), Long[].class);
        Arrays.sort(ids);
        Long[] expectedResult = Stream.concat
        (
            LongStream.rangeClosed(2, TeamGroupArgumentResolver.TEAMS_MAX - 2).boxed(),
            Stream.of((long) TeamGroupArgumentResolver.TEAMS_MAX)
        )
            .toArray(Long[]::new);
        assertArrayEquals(expectedResult, ids);
    }

    @Test
    public void testGetLadderTeams()
    throws Exception
    {
        LadderTeam[] teams = objectMapper.readValue(mvc.perform(get("/api/team/group/teams/full")
            .queryParam
            (
                "legacyUid",
                conversionService.convert
                (
                    new TeamLegacyUid
                    (
                        QueueType.LOTV_1V1,
                        Region.EU,
                        BigInteger.ZERO
                    ),
                    String.class
                )
            )
            .queryParam
            (
                "teamId",
                String.valueOf(TeamGroupArgumentResolver.TEAMS_MAX),
                String.valueOf(TeamGroupArgumentResolver.TEAMS_MAX + 1)
            )
            .queryParam("fromSeason", "2")
            .queryParam("toSeason", String.valueOf(TeamGroupArgumentResolver.TEAMS_MAX + 1)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), LadderTeam[].class);
        Arrays.sort(teams, Comparator.comparing(LadderTeam::getId));
        Assertions.assertThat(teams)
            .usingRecursiveComparison()
            .isEqualTo(new LadderTeam[]{
                LADDER_TEAMS.get(1),
                LADDER_TEAMS.get(TeamGroupArgumentResolver.TEAMS_MAX - 1)
            });
    }

    @Test
    public void whenIdsAreEmpty_thenBadRequest()
    throws Exception
    {
        mvc.perform(get("/api/team/group/flat"))
            .andExpect(status().isBadRequest());
    }

    @Test
    public void whenTeamSizeIsExceeded_thenBadRequest()
    throws Exception
    {
        String[] longIdList = LongStream.range(0, TeamGroupArgumentResolver.TEAMS_MAX + 1)
            .boxed()
            .map(String::valueOf)
            .toArray(String[]::new);
        mvc.perform(get("/api/team/group/flat").queryParam("teamId", longIdList))
            .andExpect(status().isBadRequest());
    }

    @Test
    public void whenLegacyUidSizeIsExceeded_thenBadRequest()
    throws Exception
    {
        String[] longIdList = LongStream.range(0, TeamGroupArgumentResolver.LEGACY_UIDS_MAX + 1)
            .boxed()
            .map(l->new TeamLegacyUid(QueueType.LOTV_1V1, Region.EU, BigInteger.valueOf(l)))
            .map(uid->conversionService.convert(uid, String.class))
            .toArray(String[]::new);
        mvc.perform(get("/api/team/group/flat").queryParam("legacyUid", longIdList))
            .andExpect(status().isBadRequest());
    }

    @Test
    public void whenFlatTeamSizeIsExceeded_thenBadRequest()
    throws Exception
    {

        mvc.perform(get("/api/team/group/flat")
            .queryParam
            (
                "legacyUid",
                conversionService.convert
                (
                    new TeamLegacyUid
                    (
                        QueueType.LOTV_1V1,
                        Region.EU,
                        BigInteger.ZERO
                    ),
                    String.class
                ),
                conversionService.convert
                (
                    new TeamLegacyUid
                    (
                        QueueType.LOTV_1V1,
                        Region.EU,
                        BigInteger.ONE
                    ),
                    String.class
                )
            )
            .queryParam
            (
                "teamId",
                String.valueOf(TeamGroupArgumentResolver.TEAMS_MAX - 1),
                String.valueOf(TeamGroupArgumentResolver.TEAMS_MAX),
                String.valueOf(TeamGroupArgumentResolver.TEAMS_MAX + 1)
            ))
            .andExpect(status().isBadRequest());
    }

}