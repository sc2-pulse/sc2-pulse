// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static com.nephest.battlenet.sc2.web.controller.TeamGroupController.HISTORY_TEAM_COUNT_MAX;
import static java.util.Map.entry;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.dao.LeagueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.PopulationStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistory;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistoryDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistoryDAO.GroupMode;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistoryDAO.HistoryColumn;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistoryDAO.StaticColumn;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.util.AssertionUtil;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(classes = AllTestConfig.class)
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class TeamGroupHistoryIT
{

    public static final Comparator<TeamHistory> ID_COMPARATOR = Comparator.comparing(h->{
        Object obj = h.staticData().get(StaticColumn.ID);
        return obj == null ? null : ((Number) obj).longValue();
    });
    public static final Comparator<TeamHistory> TIMESTAMP_COMPARATOR = Comparator.comparing(h->{
        List<?> objList = h.history().get(HistoryColumn.TIMESTAMP);
        if(objList == null || objList.isEmpty()) return null;

        Object obj = objList.get(0);
        return obj == null ? null : ((Number) obj).longValue();
    });

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired @Qualifier("mvcConversionService")
    private ConversionService mvcConversionService;

    @Autowired @Qualifier("sc2StatsConversionService")
    private ConversionService sc2ConversionService;

    @Autowired @Qualifier("minimalConversionService")
    private ConversionService minConversionService;

    private static List<Season> seasons;
    private static List<TeamHistory> FULL_HISTORY;
    private static List<TeamHistory> FULL_HISTORY_LEGACY_UID_GROUP;
    private static Map<GroupMode, List<TeamHistory>> REFERENCE_GROUPS;

    @BeforeAll
    public static void beforeAll
    (
        @Autowired DataSource dataSource,
        @Autowired TeamDAO teamDAO,
        @Autowired TeamStateDAO teamStateDAO,
        @Autowired PopulationStateDAO populationStateDAO,
        @Autowired LeagueStatsDAO leagueStatsDAO,
        @Autowired SeasonGenerator seasonGenerator,
        @Autowired JdbcTemplate jdbcTemplate
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
            init
            (
                teamDAO,
                teamStateDAO,
                populationStateDAO,
                leagueStatsDAO,
                seasonGenerator,
                jdbcTemplate
            );
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

    private static void init
    (
        TeamDAO teamDAO,
        TeamStateDAO teamStateDAO,
        PopulationStateDAO populationStateDAO,
        LeagueStatsDAO leagueStatsDAO,
        SeasonGenerator seasonGenerator,
        JdbcTemplate jdbcTemplate
    )
    {
        OffsetDateTime start = SC2Pulse.offsetDateTime().minusYears(1);

        List<Season> allSeasons = new ArrayList<>();
        for(int i = 0; i < 3; i++)
            for(Region region : new Region[]{Region.EU, Region.US})
                allSeasons.add(new Season(null, i + 1, region, 2020, i,
                    start.plusDays(i), start.plusDays(i + 1)));
        seasons = allSeasons.stream()
            .filter(season->season.getRegion() == Region.EU)
            .toList();
        seasonGenerator.generateSeason
        (
            allSeasons,
            List.of(BaseLeague.LeagueType.BRONZE, BaseLeague.LeagueType.SILVER),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            3
        );
        seasonGenerator.generateSeason
        (
            allSeasons,
            List.of(BaseLeague.LeagueType.MASTER),
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.SECOND,
            0
        );

        jdbcTemplate.update("DELETE FROM team_state");
        jdbcTemplate.update("UPDATE team SET legacy_id = 11 WHERE id IN(1, 13, 25)");
        jdbcTemplate.update("UPDATE team SET legacy_id = 12 WHERE id IN(2, 14, 26)");
        jdbcTemplate.update("UPDATE team SET legacy_id = 13 WHERE id IN(3, 15, 27)");
        jdbcTemplate.update("UPDATE team SET last_played = null WHERE legacy_id = 11");

        //take team snapshots
        for(int i = 0; i < seasons.size(); i++)
        {
            //12 teams per season
            List<Long> seasonTeamIds = LongStream.range(12L * i, 12L * i + 12)
                .boxed()
                .toList();
            teamStateDAO.takeSnapshot(seasonTeamIds, seasons.get(i).getStart().plusMinutes(1));
        }
        //take snapshot with different vals to verify that they are returned properly
        teamDAO.merge(Set.of(new Team(
            null,
            2,
            Region.EU,
            new BaseLeague
            (
                BaseLeague.LeagueType.MASTER,
                QueueType.LOTV_1V1,
                TeamType.ARRANGED
            ),
            BaseLeagueTier.LeagueTierType.SECOND,
            BigInteger.valueOf(11L),
            15,
            112L, 13, 14, 15, 0,
            null
        )));
        //take new team snapshots with ranks to verify them
        for(int i = 0; i < seasons.size(); i++)
        {
            int seasonId = i + 1;
            leagueStatsDAO.mergeCalculateForSeason(seasonId);
            populationStateDAO.takeSnapshot(List.of(seasonId));
            teamDAO.updateRanks(seasonId);
            List<Long> seasonTeamIds = LongStream.range(12L * i, 12L * i + 12)
                .boxed()
                .toList();
            teamStateDAO.takeSnapshot(seasonTeamIds, seasons.get(i).getStart().plusMinutes(2));
        }
        //set new team values to verify they are properly added at the end of a season
        teamDAO.merge(Set.of(new Team(
            null,
            2,
            Region.EU,
            new BaseLeague
            (
                BaseLeague.LeagueType.SILVER,
                QueueType.LOTV_1V1,
                TeamType.ARRANGED
            ),
            BaseLeagueTier.LeagueTierType.FIRST,
            BigInteger.valueOf(11L),
            7,
            113L, 14, 15, 16, 0,
            null
        )));
        //add one more team to change differentiate ranks from previous season
        teamDAO.merge(Set.of(new Team(
            null,
            2,
            Region.EU,
            new BaseLeague
            (
                BaseLeague.LeagueType.SILVER,
                QueueType.LOTV_1V1,
                TeamType.ARRANGED
            ),
            BaseLeagueTier.LeagueTierType.FIRST,
            BigInteger.valueOf(991L),
            7,
            114L, 14, 15, 16, 0,
            null
        )));
        //take final team snapshots with ranks to verify them
        for(int i = 0; i < seasons.size(); i++)
        {
            int seasonId = i + 1;
            leagueStatsDAO.mergeCalculateForSeason(seasonId);
            populationStateDAO.takeSnapshot(List.of(seasonId));
            teamDAO.updateRanks(seasonId);
            if(i + 1 < seasons.size()) //exclude last season
            {
                List<Long> seasonTeamIds = LongStream.range(12L * i, 12L * i + 12).boxed().toList();
                teamStateDAO.takeSnapshot(seasonTeamIds, seasons.get(i).getEnd());
            }
        }
        FULL_HISTORY = getFullTeamHistory();
        FULL_HISTORY_LEGACY_UID_GROUP = List.of(new TeamHistory
        (
            Stream.of(StaticColumn.QUEUE, StaticColumn.REGION, StaticColumn.LEGACY_ID)
                .collect(Collectors.toMap(
                    Function.identity(),
                    col->FULL_HISTORY.get(0).staticData().get(col),
                    (l, r)->{throw new IllegalStateException("Unexpected merge");},
                    ()->new EnumMap<StaticColumn, Object>(StaticColumn.class)
                )),
            Arrays.stream(HistoryColumn.values())
                .collect(Collectors.toMap(
                    Function.identity(),
                    col->FULL_HISTORY.stream()
                        .flatMap(h->h.history().get(col).stream())
                        .toList(),
                    (l, r)->{throw new IllegalStateException("Unexpected merge");},
                    ()->new EnumMap<>(HistoryColumn.class)
                ))
        ));
        REFERENCE_GROUPS = Map.of
        (
            GroupMode.TEAM, FULL_HISTORY,
            GroupMode.LEGACY_UID, FULL_HISTORY_LEGACY_UID_GROUP
        );
    }

    private static List<TeamHistory> getFullTeamHistory()
    {
        return List.of
        (
            new TeamHistory
            (
                Map.of
                (
                    StaticColumn.ID, 1L,
                    StaticColumn.SEASON, 1,
                    StaticColumn.LEGACY_ID, 11,

                    StaticColumn.REGION, 2,

                    StaticColumn.QUEUE, 201
                ),
                Map.ofEntries
                (
                    entry
                    (
                        HistoryColumn.TIMESTAMP,
                        List.of
                        (
                            seasons.get(0).getStart().plusMinutes(1).toEpochSecond(),
                            seasons.get(0).getStart().plusMinutes(2).toEpochSecond(),
                            seasons.get(0).getEnd().toEpochSecond()
                        )
                    ),

                    entry(TeamHistoryDAO.HistoryColumn.DIVISION_ID, List.of(1, 1, 1)),
                    entry(HistoryColumn.RATING, List.of(0, 0, 0)),
                    entry(HistoryColumn.WINS, Stream.of(0, 0, 0).toList()),
                    entry(HistoryColumn.GAMES, List.of(3, 3, 3)),
                    entry(HistoryColumn.LEAGUE, List.of(0, 0, 0)),
                    entry(HistoryColumn.TIER, List.of(0, 0, 0)),

                    entry(HistoryColumn.RANK_GLOBAL, Stream.of(null, 12, 12).toList()),
                    entry(HistoryColumn.COUNT_GLOBAL, Stream.of(null, 12, 12).toList()),
                    entry(HistoryColumn.RANK_REGION, Stream.of(null, 6, 6).toList()),
                    entry(HistoryColumn.COUNT_REGION, Stream.of(null, 6, 6).toList()),
                    entry(HistoryColumn.RANK_LEAGUE, Stream.of(null, 3, 3).toList()),
                    entry(HistoryColumn.COUNT_LEAGUE, Stream.of(null, 3, 3).toList()),

                    entry(HistoryColumn.ID, List.of(1L, 1L, 1L)),
                    entry(HistoryColumn.SEASON, List.of(1, 1, 1))
                )
            ),
            new TeamHistory
            (
                Map.of
                (
                    StaticColumn.ID, 13L,
                    StaticColumn.SEASON, 2,
                    StaticColumn.LEGACY_ID, 11,

                    StaticColumn.REGION, 2,

                    StaticColumn.QUEUE, 201
                ),
                Map.ofEntries
                (
                    entry
                    (
                        HistoryColumn.TIMESTAMP,
                        List.of
                        (
                            seasons.get(1).getStart().plusMinutes(1).toEpochSecond(),
                            seasons.get(1).getStart().plusMinutes(2).toEpochSecond(),
                            seasons.get(1).getEnd().toEpochSecond()
                        )
                    ),

                    entry(HistoryColumn.DIVISION_ID, List.of(5, 15, 7)),
                    entry(HistoryColumn.RATING, List.of(12, 112, 113)),
                    entry(HistoryColumn.WINS, List.of(12, 13, 14)),
                    entry(HistoryColumn.GAMES, List.of(39, 42, 45)),
                    entry(HistoryColumn.LEAGUE, List.of(0, 5, 1)),
                    entry(HistoryColumn.TIER, List.of(0, 1, 0)),

                    entry(HistoryColumn.RANK_GLOBAL, Stream.of(null, 1, 2).toList()),
                    entry(HistoryColumn.COUNT_GLOBAL, Stream.of(null, 12, 13).toList()),
                    entry(HistoryColumn.RANK_REGION, Stream.of(null, 1, 2).toList()),
                    entry(HistoryColumn.COUNT_REGION, Stream.of(null, 6, 7).toList()),
                    entry(HistoryColumn.RANK_LEAGUE, Stream.of(null, 1, 2).toList()),
                    entry(HistoryColumn.COUNT_LEAGUE, Stream.of(null, 1, 4).toList()),

                    entry(HistoryColumn.ID, List.of(13L, 13L, 13L)),
                    entry(HistoryColumn.SEASON, List.of(2, 2, 2))
                )
            ),
            new TeamHistory
            (
                Map.of
                (
                    StaticColumn.ID, 25L,
                    StaticColumn.SEASON, 3,
                    StaticColumn.LEGACY_ID, 11,

                    StaticColumn.REGION, 2,

                    StaticColumn.QUEUE, 201
                ),
                //current season team should be excluded. Snapshots only.
                Map.ofEntries
                (
                    entry
                    (
                        HistoryColumn.TIMESTAMP,
                        List.of
                        (
                            seasons.get(2).getStart().plusMinutes(1).toEpochSecond(),
                            seasons.get(2).getStart().plusMinutes(2).toEpochSecond()
                        )
                    ),

                    entry(TeamHistoryDAO.HistoryColumn.DIVISION_ID, List.of(9, 9)),
                    entry(HistoryColumn.RATING, List.of(24, 24)),
                    entry(HistoryColumn.WINS, Stream.of(24, 24).toList()),
                    entry(HistoryColumn.GAMES, List.of(75, 75)),
                    entry(HistoryColumn.LEAGUE, List.of(0, 0)),
                    entry(HistoryColumn.TIER, List.of(0, 0)),

                    entry(HistoryColumn.RANK_GLOBAL, Stream.of(null, 12).toList()),
                    entry(HistoryColumn.COUNT_GLOBAL, Stream.of(null, 12).toList()),
                    entry(HistoryColumn.RANK_REGION, Stream.of(null, 6).toList()),
                    entry(HistoryColumn.COUNT_REGION, Stream.of(null, 6).toList()),
                    entry(HistoryColumn.RANK_LEAGUE, Stream.of(null, 3).toList()),
                    entry(HistoryColumn.COUNT_LEAGUE, Stream.of(null, 3).toList()),

                    entry(HistoryColumn.ID, List.of(25L, 25L)),
                    entry(HistoryColumn.SEASON, List.of(3, 3))
                )
            )
        );
    }

    @EnumSource(GroupMode.class)
    @ParameterizedTest
    public void testDefaultFullHistory(GroupMode groupMode)
    throws Exception
    {
        List<TeamHistory> found = objectMapper.readValue(mvc.perform
        (
            get("/api/team/group/history")
                .queryParam
                (
                    "legacyUid",
                    mvcConversionService.convert
                    (
                        new TeamLegacyUid
                        (
                            QueueType.LOTV_1V1,
                            Region.EU,
                            BigInteger.valueOf(11L)
                        ),
                        String.class
                    )
                )
                .queryParam
                (
                    "history",
                    Arrays.stream(HistoryColumn.values())
                        .map(c->mvcConversionService.convert(c, String.class))
                        .toArray(String[]::new)
                )
                .queryParam
                (
                    "static",
                    groupMode.getSupportedStaticColumns().stream()
                        .map(c->mvcConversionService.convert(c, String.class))
                        .toArray(String[]::new)
                )
                .queryParam
                (
                    "groupBy",
                    mvcConversionService.convert(groupMode, String.class)
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        found.sort(TIMESTAMP_COMPARATOR);

        Assertions.assertThat(found)
            .usingRecursiveComparison()
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.TIMESTAMP")
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.ID")
            .withEqualsForFields(AssertionUtil::numberEquals, "staticData.ID")
            .isEqualTo(REFERENCE_GROUPS.get(groupMode));
    }

    public static Stream<Arguments> testToAndFromFilters()
    {
        return Stream.of
        (
            Arguments.of
            (
                seasons.get(0).getStart().plusMinutes(1),
                null,
                FULL_HISTORY
            ),
            Arguments.of
            (
                seasons.get(1).getStart().plusMinutes(1),
                null,
                FULL_HISTORY.subList(1, 3)
            ),
            Arguments.of
            (
                seasons.get(2).getStart().plusMinutes(1),
                null,
                FULL_HISTORY.subList(2, 3)
            ),

            Arguments.of
            (
                seasons.get(1).getStart().plusMinutes(2),
                null,
                List.of(subList(FULL_HISTORY.get(1), 1, 3), FULL_HISTORY.get(2))
            ),
            Arguments.of
            (
                seasons.get(1).getStart().plusMinutes(3),
                null,
                List.of(subList(FULL_HISTORY.get(1), 2, 3), FULL_HISTORY.get(2))
            ),

            Arguments.of
            (
                null,
                seasons.get(1).getStart().plusSeconds(1),
                FULL_HISTORY.subList(0, 1)
            ),
            Arguments.of
            (
                null,
                seasons.get(0).getStart().plusMinutes(3),
                List.of(subList(FULL_HISTORY.get(0), 0, 2))
            ),
            Arguments.of
            (
                null,
                seasons.get(0).getStart().plusMinutes(2),
                List.of(subList(FULL_HISTORY.get(0), 0, 1))
            ),

            Arguments.of
            (
                seasons.get(0).getStart().plusMinutes(3),
                seasons.get(2).getStart().plusMinutes(2),
                List.of
                (
                    subList(FULL_HISTORY.get(0), 2, 3),
                    FULL_HISTORY.get(1),
                    subList(FULL_HISTORY.get(2), 0, 1)
                )
            )
        );
    }

    public static Map<HistoryColumn, List<?>> subList
    (
        Map<TeamHistoryDAO.HistoryColumn, List<?>> history,
        int from, int to
    )
    {
        Map<TeamHistoryDAO.HistoryColumn, List<?>> sub = new EnumMap<>(history);
        for(Map.Entry<HistoryColumn, List<?>> e : sub.entrySet())
            e.setValue(e.getValue().subList(from, to));
        return sub;
    }

    public static TeamHistory subList(TeamHistory history, int from, int to)
    {
        return new TeamHistory
        (
            history.staticData(),
            subList(history.history(), from, to)
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testToAndFromFilters
    (
        OffsetDateTime from,
        OffsetDateTime to,
        List<TeamHistory> expected
    )
    throws Exception
    {
        List<TeamHistory> found = objectMapper.readValue(mvc.perform
        (
            get("/api/team/group/history")
                .queryParam
                (
                    "legacyUid",
                    mvcConversionService.convert
                    (
                        new TeamLegacyUid
                        (
                            QueueType.LOTV_1V1,
                            Region.EU,
                            BigInteger.valueOf(11L)
                        ),
                        String.class
                    )
                )
                .queryParam
                (
                    "history",
                    Arrays.stream(HistoryColumn.values())
                        .map(c->mvcConversionService.convert(c, String.class))
                        .toArray(String[]::new)
                )
                .queryParam
                (
                    "static",
                    Arrays.stream(StaticColumn.values())
                        .map(c->mvcConversionService.convert(c, String.class))
                        .toArray(String[]::new)
                )
                .queryParam("from", mvcConversionService.convert(from, String.class))
                .queryParam("to", mvcConversionService.convert(to, String.class))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        found.sort(ID_COMPARATOR);
        Assertions.assertThat(found)
            .usingRecursiveComparison()
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.TIMESTAMP")
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.ID")
            .withEqualsForFields(AssertionUtil::numberEquals, "staticData.ID")
            .isEqualTo(expected);
    }

    public static Stream<Arguments> testSingleHistoryColumn()
    {
        return Arrays.stream(GroupMode.values())
            .flatMap(groupMode->Arrays.stream(HistoryColumn.values()).map(c->Arguments.of(c, groupMode)));
    }

    @MethodSource
    @ParameterizedTest
    public void testSingleHistoryColumn(HistoryColumn column, GroupMode groupMode)
    throws Exception
    {
        boolean idSupported = groupMode.isSupported(StaticColumn.ID);
        List<TeamHistory> found = objectMapper.readValue(mvc.perform
        (
            get("/api/team/group/history")
                .queryParam
                (
                    "legacyUid",
                    mvcConversionService.convert
                    (
                        new TeamLegacyUid
                        (
                            QueueType.LOTV_1V1,
                            Region.EU,
                            BigInteger.valueOf(11L)
                        ),
                        String.class
                    )
                )
                .queryParam
                (
                    "static",
                    idSupported
                        ? mvcConversionService.convert(StaticColumn.ID, String.class)
                        : null
                )
                .queryParam
                (
                    "history",
                    mvcConversionService.convert(column, String.class)
                )
                .queryParam
                (
                    "groupBy",
                    mvcConversionService.convert(groupMode, String.class)
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        if(idSupported) found.sort(ID_COMPARATOR);

        Assertions.assertThat(found)
            .usingRecursiveComparison()
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.TIMESTAMP")
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.ID")
            .withEqualsForFields(AssertionUtil::numberEquals, "staticData.ID")
            .isEqualTo(REFERENCE_GROUPS.get(groupMode).stream()
                .map(h->new TeamHistory(
                    idSupported
                        ? Map.of(StaticColumn.ID, h.staticData().get(StaticColumn.ID))
                        : Map.of(),
                    Map.of(column, h.history().get(column))))
                .toList()
            );
    }

    public static Stream<Arguments> testSingleStaticColumn()
    {
        return Arrays.stream(GroupMode.values())
            .flatMap(groupMode->Arrays.stream(StaticColumn.values())
                .filter(groupMode::isSupported)
                .map(c->Arguments.of(c, groupMode)));
    }

    @MethodSource
    @ParameterizedTest
    public void testSingleStaticColumn(StaticColumn column, GroupMode groupMode)
    throws Exception
    {
        List<TeamHistory> found = objectMapper.readValue(mvc.perform
        (
            get("/api/team/group/history")
                .queryParam
                (
                    "legacyUid",
                    mvcConversionService.convert
                    (
                        new TeamLegacyUid
                        (
                            QueueType.LOTV_1V1,
                            Region.EU,
                            BigInteger.valueOf(11L)
                        ),
                        String.class
                    )
                )
                .queryParam
                (
                    "history",
                    mvcConversionService.convert(HistoryColumn.TIMESTAMP, String.class)
                )
                .queryParam
                (
                    "static",
                    mvcConversionService.convert(column, String.class)
                )
                .queryParam
                (
                    "groupBy",
                    mvcConversionService.convert(groupMode, String.class)
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        found.sort(TIMESTAMP_COMPARATOR);

        Assertions.assertThat(found)
            .usingRecursiveComparison()
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.TIMESTAMP")
            .withEqualsForFields(AssertionUtil::numberEquals, "staticData.ID")
            .isEqualTo(REFERENCE_GROUPS.get(groupMode).stream()
                .map(h->new TeamHistory(
                    Map.of(column, h.staticData().get(column)),
                    Map.of(HistoryColumn.TIMESTAMP, h.history().get(HistoryColumn.TIMESTAMP))))
                .toList()
            );
    }

    public static Stream<Arguments> verifyParameterValidation()
    {
        OffsetDateTime now = SC2Pulse.offsetDateTime();
        return Stream.of
        (
            Arguments.of
            (
                "Validation failure",
                Map.of
                (
                    "teamId", LongStream.range(0, HISTORY_TEAM_COUNT_MAX + 1).toArray(),
                    "history", HistoryColumn.TIMESTAMP
                )
            ),
            Arguments.of
            (
                "At least one group id is required",
                Map.of("history", HistoryColumn.TIMESTAMP)
            ),
            Arguments.of
            (
                "Required parameter 'history' is not present",
                Map.of("teamId", 1L)
            ),
            Arguments.of
            (
                "'from' parameter must be before 'to' parameter",
                Map.of
                (
                    "teamId", 1L,
                    "history", HistoryColumn.TIMESTAMP,
                    "from", now,
                    "to", now.minusSeconds(1)
                )
            ),
            Arguments.of
            (
                "'from' parameter must be before 'to' parameter",
                Map.of
                (
                    "teamId", 1L,
                    "history", HistoryColumn.TIMESTAMP,
                    "from", now,
                    "to", now
                )
            ),
            Arguments.of
            (
                "Some static columns are not supported by the group mode",
                Map.of
                (
                    "teamId", 1L,
                    "history", HistoryColumn.TIMESTAMP,
                    "static", StaticColumn.ID,
                    "groupBy", GroupMode.LEGACY_UID
                )
            ),
            Arguments.of
            (
                "Some static columns are not supported by the group mode",
                Map.of
                (
                    "teamId", 1L,
                    "history", HistoryColumn.TIMESTAMP,
                    "static", StaticColumn.SEASON,
                    "groupBy", GroupMode.LEGACY_UID
                )
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    public void verifyParameterValidation(String errorFragment, Map<String, Object> parameters)
    throws Exception
    {
        MockHttpServletRequestBuilder req = get("/api/team/group/history")
            .contentType(MediaType.APPLICATION_JSON);
        for(Map.Entry<String, Object> entry : parameters.entrySet())
            req = req.queryParam
            (
                entry.getKey(),
                mvcConversionService.convert(entry.getValue(), String.class)
            );

        mvc.perform(req)
            .andExpect(status().isBadRequest())
            .andExpect(content().string(Matchers.containsString(errorFragment)));
    }

}
