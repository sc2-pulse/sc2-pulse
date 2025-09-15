// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static com.nephest.battlenet.sc2.web.controller.TeamController.HISTORY_TEAM_COUNT_MAX;
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
import com.nephest.battlenet.sc2.model.local.inner.ConvertedTeamHistoryHistoryData;
import com.nephest.battlenet.sc2.model.local.inner.ConvertedTeamHistoryStaticData;
import com.nephest.battlenet.sc2.model.local.inner.RawTeamHistoryHistoryData;
import com.nephest.battlenet.sc2.model.local.inner.RawTeamHistoryStaticData;
import com.nephest.battlenet.sc2.model.local.inner.RawTeamHistorySummaryData;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistory;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistoryDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistoryDAO.GroupMode;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistoryDAO.HistoryColumn;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistoryDAO.StaticColumn;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistoryDAO.SummaryColumn;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistorySummary;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyId;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import com.nephest.battlenet.sc2.model.local.inner.TypedTeamHistorySummaryData;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.util.AssertionUtil;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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

    public static final Comparator<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> ID_COMPARATOR
        = Comparator.comparing(h->{
            Object obj = h.staticData().data().get(StaticColumn.ID);
            return obj == null ? null : ((Number) obj).longValue();
    });
    public static final Comparator<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> TIMESTAMP_COMPARATOR
        = Comparator.comparing(h->{
            List<?> objList = h.history().data().get(HistoryColumn.TIMESTAMP);
            if(objList == null || objList.isEmpty()) return null;

            Object obj = objList.get(0);
            return obj == null ? null : ((Number) obj).longValue();
    });
    public static final Comparator<TeamHistorySummary<RawTeamHistoryStaticData, RawTeamHistorySummaryData>> ID_SUMMARY_COMPARATOR
        = Comparator.comparing(s->{
            Object obj = s.staticData().data().get(StaticColumn.ID);
            return obj == null ? null : ((Number) obj).longValue();
    });
    public static final Comparator<TeamHistorySummary<RawTeamHistoryStaticData, RawTeamHistorySummaryData>> MIN_RATING_SUMMARY_COMPARATOR
        = Comparator.comparing(s->{
            Object obj = s.summary().data().get(SummaryColumn.RATING_MIN);
            return obj == null ? null : ((Number) obj).intValue();
    });

    @Autowired
    private TeamHistoryDAO teamHistoryDAO;

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
    private static List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> FULL_HISTORY;
    private static List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> FULL_HISTORY_LEGACY_UID_GROUP;
    private static Map<GroupMode, List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>>> REFERENCE_GROUPS;
    private static Map<GroupMode, List<TeamHistorySummary<RawTeamHistoryStaticData, RawTeamHistorySummaryData>>> REFERENCE_SUMMARY_GROUPS;

    @BeforeAll
    public static void beforeAll
    (
        @Autowired DataSource dataSource,
        @Autowired TeamDAO teamDAO,
        @Autowired TeamStateDAO teamStateDAO,
        @Autowired PopulationStateDAO populationStateDAO,
        @Autowired LeagueStatsDAO leagueStatsDAO,
        @Autowired SeasonGenerator seasonGenerator,
        @Autowired JdbcTemplate jdbcTemplate,
        @Autowired @Qualifier("mvcConversionService") ConversionService mvcConversionService
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
                jdbcTemplate,
                mvcConversionService
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
        JdbcTemplate jdbcTemplate,
        ConversionService mvcConversionService
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
        jdbcTemplate.update("UPDATE team SET legacy_id = '1.11.1' WHERE id IN(1, 13, 25)");
        jdbcTemplate.update("UPDATE team SET legacy_id = '12' WHERE id IN(2, 14, 26)");
        jdbcTemplate.update("UPDATE team SET legacy_id = '13' WHERE id IN(3, 15, 27)");
        jdbcTemplate.update("UPDATE team SET last_played = null WHERE legacy_id = '1.11.1'");

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
            TeamLegacyId.trusted("1.11.1"),
            15,
            112L, 13, 14, 15, 0,
            null, SC2Pulse.offsetDateTime(), SC2Pulse.offsetDateTime()
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
            TeamLegacyId.trusted("1.11.1"),
            7,
            113L, 14, 15, 16, 0,
            null, SC2Pulse.offsetDateTime(), SC2Pulse.offsetDateTime()
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
            TeamLegacyId.trusted("991"),
            7,
            114L, 14, 15, 16, 0,
            null, SC2Pulse.offsetDateTime(), SC2Pulse.offsetDateTime()
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
        FULL_HISTORY = getFullTeamHistory(mvcConversionService);
        FULL_HISTORY_LEGACY_UID_GROUP = List.of(new TeamHistory<>
        (
            new RawTeamHistoryStaticData(Stream.of(
                StaticColumn.QUEUE_TYPE,
                StaticColumn.TEAM_TYPE,
                StaticColumn.REGION,
                StaticColumn.LEGACY_ID
            )
                .collect(Collectors.toMap(
                    Function.identity(),
                    col->FULL_HISTORY.get(0).staticData().data().get(col),
                    (l, r)->{throw new IllegalStateException("Unexpected merge");},
                    ()->new EnumMap<StaticColumn, Object>(StaticColumn.class)
                ))),
            new RawTeamHistoryHistoryData(Arrays.stream(HistoryColumn.values())
                .collect(Collectors.toMap(
                    Function.identity(),
                    col->FULL_HISTORY.stream()
                        .flatMap(h->h.history().data().get(col).stream())
                        .toList(),
                    (l, r)->{throw new IllegalStateException("Unexpected merge");},
                    ()->new EnumMap<>(HistoryColumn.class)
                )))
        ));
        REFERENCE_GROUPS = Map.of
        (
            GroupMode.TEAM, FULL_HISTORY,
            GroupMode.LEGACY_UID, FULL_HISTORY_LEGACY_UID_GROUP
        );
        REFERENCE_SUMMARY_GROUPS = REFERENCE_GROUPS.entrySet().stream().collect(
            Collectors.toMap(
                Map.Entry::getKey,
                e->e.getValue().stream().map(TeamGroupHistoryIT::toSummary).toList(),
                (l, r)->{throw new IllegalStateException("Unexpected merge");},
                ()->new EnumMap<>(GroupMode.class)
            ));
    }

    private static TeamHistorySummary<RawTeamHistoryStaticData, RawTeamHistorySummaryData> toSummary
    (
        TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData> history
    )
    {
        return new TeamHistorySummary<>(history.staticData(), calculateSummary(history.history()));
    }

    public static <T> Stream<T> mapValues
    (
        Collection<?> history,
        Function<Number, T> mapper
    )
    {
        return history.stream()
            .map(TeamGroupHistoryIT::mapNumberValue)
            .map(n->n != null ? mapper.apply(n) : null);
    }

    public static Number mapNumberValue(Object obj)
    {
        return obj != null ? (Number) obj : null;
    }

    private static RawTeamHistorySummaryData calculateSummary
    (
        RawTeamHistoryHistoryData data
    )
    {
        Map<HistoryColumn, List<?>> history = data.data();
        List<Integer> ranks =
            mapValues(history.get(HistoryColumn.REGION_RANK), Number::intValue).toList();
        List<Integer> teamCount =
            mapValues(history.get(HistoryColumn.REGION_TEAM_COUNT), Number::intValue).toList();
        history = playerActionsOnly(history);
        List<Integer> rating = mapValues(history.get(HistoryColumn.RATING), Number::intValue).toList();
        if(rating.isEmpty()) return RawTeamHistorySummaryData.EMPTY;

        List<Long> teamId = mapValues(history.get(HistoryColumn.ID), Number::longValue).toList();
        List<Integer> games = mapValues(history.get(HistoryColumn.GAMES), Number::intValue).toList();
        Map<SummaryColumn, Object> summary = new EnumMap<>(SummaryColumn.class);
        summary.put(SummaryColumn.GAMES, calculateGames(teamId, rating, games));
        summary.put(SummaryColumn.RATING_MIN, Collections.min(rating));
        summary.put(SummaryColumn.RATING_MAX, Collections.max(rating));
        summary.put(SummaryColumn.RATING_AVG, rating.stream().mapToInt(i->i).average().orElseThrow());
        summary.put(SummaryColumn.RATING_LAST, rating.get(rating.size() - 1));
        summary.put(SummaryColumn.REGION_RANK_LAST, ranks.get(ranks.size() - 1));
        summary.put(SummaryColumn.REGION_TEAM_COUNT_LAST, teamCount.get(teamCount.size() - 1));
        return new RawTeamHistorySummaryData(Collections.unmodifiableMap(summary));
    }

    private static Map<TeamHistoryDAO.HistoryColumn, List<?>> playerActionsOnly
    (
        Map<TeamHistoryDAO.HistoryColumn, List<?>> history
    )
    {
        List<Integer> rating = mapValues(history.get(HistoryColumn.RATING), Number::intValue).toList();
        List<Long> teamId = mapValues(history.get(HistoryColumn.ID), Number::longValue).toList();
        List<Integer> games = mapValues(history.get(HistoryColumn.GAMES), Number::intValue).toList();
        List<Integer> validIx = IntStream.range(0, rating.size())
            .filter(i->i == 0
                || !games.get(i).equals(games.get(i - 1))
                || !rating.get(i).equals(rating.get(i - 1))
                || !teamId.get(i).equals(teamId.get(i - 1)))
            .boxed()
            .toList();
        if(validIx.size() == rating.size()) return history;

        return history.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                vals->validIx.stream()
                    .map(ix->vals.getValue().get(ix))
                    .toList(),
                (l, r)->{throw new IllegalStateException("Unexpected merge");},
                ()->new EnumMap<>(HistoryColumn.class)
            ));
    }

    private static Integer calculateGames
    (
        List<Long> teamId,
        List<Integer> rating,
        List<Integer> games
    )
    {

        List<Integer> gamesDiff = new ArrayList<>(games.size());
        for (int i = 0; i < games.size(); i++)
        {
            int prevI = i - 1;
            gamesDiff.add(i == 0
                ? 1
                : !teamId.get(i).equals(teamId.get(prevI))
                    || games.get(i).equals(games.get(prevI))
                        && !rating.get(i).equals(rating.get(prevI))
                    || games.get(i) - games.get(prevI) < 0
                        ? games.get(i)
                        : games.get(i) - games.get(prevI));
        }
        return gamesDiff.stream()
            .mapToInt(i->i)
            .sum();
    }

    private static List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> getFullTeamHistory
    (
        ConversionService conversionService
    )
    {
        String legacyUid = conversionService.convert
        (
            new TeamLegacyUid
            (
                QueueType.LOTV_1V1,
                TeamType.ARRANGED,
                Region.EU,
                "1.11.1"
            ),
            String.class
        );
        return List.of
        (
            new TeamHistory<>
            (
                new RawTeamHistoryStaticData(Map.of(
                    StaticColumn.ID, 1L,
                    StaticColumn.SEASON, 1,
                    StaticColumn.LEGACY_ID, "1.11.1",

                    StaticColumn.REGION, 2,

                    StaticColumn.QUEUE_TYPE, 201,
                    StaticColumn.TEAM_TYPE, 0,
                    StaticColumn.LEGACY_UID, legacyUid
                )),
                new RawTeamHistoryHistoryData(Map.ofEntries(
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
                    entry(HistoryColumn.LEAGUE_TYPE, List.of(0, 0, 0)),
                    entry(HistoryColumn.TIER_TYPE, List.of(0, 0, 0)),

                    entry(HistoryColumn.GLOBAL_RANK, Stream.of(null, 12, 12).toList()),
                    entry(HistoryColumn.GLOBAL_TEAM_COUNT, Stream.of(null, 12, 12).toList()),
                    entry(HistoryColumn.REGION_RANK, Stream.of(null, 6, 6).toList()),
                    entry(HistoryColumn.REGION_TEAM_COUNT, Stream.of(null, 6, 6).toList()),
                    entry(HistoryColumn.LEAGUE_RANK, Stream.of(null, 3, 3).toList()),
                    entry(HistoryColumn.LEAGUE_TEAM_COUNT, Stream.of(null, 3, 3).toList()),

                    entry(HistoryColumn.ID, List.of(1L, 1L, 1L)),
                    entry(HistoryColumn.SEASON, List.of(1, 1, 1))
                ))
            ),
            new TeamHistory<>
            (
                new RawTeamHistoryStaticData(Map.of(
                    StaticColumn.ID, 13L,
                    StaticColumn.SEASON, 2,
                    StaticColumn.LEGACY_ID, "1.11.1",

                    StaticColumn.REGION, 2,

                    StaticColumn.QUEUE_TYPE, 201,
                    StaticColumn.TEAM_TYPE, 0,
                    StaticColumn.LEGACY_UID, legacyUid
                )),
                new RawTeamHistoryHistoryData(Map.ofEntries(
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
                    entry(HistoryColumn.LEAGUE_TYPE, List.of(0, 5, 1)),
                    entry(HistoryColumn.TIER_TYPE, List.of(0, 1, 0)),

                    entry(HistoryColumn.GLOBAL_RANK, Stream.of(null, 1, 2).toList()),
                    entry(HistoryColumn.GLOBAL_TEAM_COUNT, Stream.of(null, 12, 13).toList()),
                    entry(HistoryColumn.REGION_RANK, Stream.of(null, 1, 2).toList()),
                    entry(HistoryColumn.REGION_TEAM_COUNT, Stream.of(null, 6, 7).toList()),
                    entry(HistoryColumn.LEAGUE_RANK, Stream.of(null, 1, 2).toList()),
                    entry(HistoryColumn.LEAGUE_TEAM_COUNT, Stream.of(null, 1, 4).toList()),

                    entry(HistoryColumn.ID, List.of(13L, 13L, 13L)),
                    entry(HistoryColumn.SEASON, List.of(2, 2, 2))
                ))
            ),
            new TeamHistory<>
            (
                new RawTeamHistoryStaticData(Map.of(
                    StaticColumn.ID, 25L,
                    StaticColumn.SEASON, 3,
                    StaticColumn.LEGACY_ID, "1.11.1",

                    StaticColumn.REGION, 2,

                    StaticColumn.QUEUE_TYPE, 201,
                    StaticColumn.TEAM_TYPE, 0,
                    StaticColumn.LEGACY_UID, legacyUid
                )),
                //current season team should be excluded. Snapshots only.
                new RawTeamHistoryHistoryData(Map.ofEntries(
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
                    entry(HistoryColumn.LEAGUE_TYPE, List.of(0, 0)),
                    entry(HistoryColumn.TIER_TYPE, List.of(0, 0)),

                    entry(HistoryColumn.GLOBAL_RANK, Stream.of(null, 12).toList()),
                    entry(HistoryColumn.GLOBAL_TEAM_COUNT, Stream.of(null, 12).toList()),
                    entry(HistoryColumn.REGION_RANK, Stream.of(null, 6).toList()),
                    entry(HistoryColumn.REGION_TEAM_COUNT, Stream.of(null, 6).toList()),
                    entry(HistoryColumn.LEAGUE_RANK, Stream.of(null, 3).toList()),
                    entry(HistoryColumn.LEAGUE_TEAM_COUNT, Stream.of(null, 3).toList()),

                    entry(HistoryColumn.ID, List.of(25L, 25L)),
                    entry(HistoryColumn.SEASON, List.of(3, 3))
                ))
            )
        );
    }

    @EnumSource(GroupMode.class)
    @ParameterizedTest
    public void testDefaultFullHistory(GroupMode groupMode)
    throws Exception
    {
        List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> found
            = objectMapper.readValue(mvc.perform(get("/api/team-histories")
                .queryParam
                (
                    "teamLegacyUid",
                    mvcConversionService.convert
                    (
                        new TeamLegacyUid
                        (
                            QueueType.LOTV_1V1,
                            TeamType.ARRANGED,
                            Region.EU,
                            "1.11.1"
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
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.data.TIMESTAMP")
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.data.ID")
            .withEqualsForFields(AssertionUtil::numberEquals, "staticData.data.ID")
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
            ),

            Arguments.of
            (
                seasons.get(seasons.size() - 1).getEnd(),
                null,
                List.of()
            ),
            Arguments.of
            (
                null,
                seasons.get(0).getStart(),
                List.of()
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

    public static TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData> subList
    (
        TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData> history,
        int from,
        int to
    )
    {
        return new TeamHistory<>
        (
            history.staticData(),
            new RawTeamHistoryHistoryData(subList(history.history().data(), from, to))
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testToAndFromFilters
    (
        OffsetDateTime from,
        OffsetDateTime to,
        List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> expected
    )
    throws Exception
    {
        List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> found =
            objectMapper.readValue( mvc.perform(
                get("/api/team-histories")
                    .queryParam
                    (
                        "teamLegacyUid",
                        mvcConversionService.convert
                        (
                            new TeamLegacyUid
                            (
                                QueueType.LOTV_1V1,
                                TeamType.ARRANGED,
                                Region.EU,
                                "1.11.1"
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
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.data.TIMESTAMP")
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.data.ID")
            .withEqualsForFields(AssertionUtil::numberEquals, "staticData.data.ID")
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
        List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> found
            = objectMapper.readValue(mvc.perform(get("/api/team-histories")
                .queryParam
                (
                    "teamLegacyUid",
                    mvcConversionService.convert
                    (
                        new TeamLegacyUid
                        (
                            QueueType.LOTV_1V1,
                            TeamType.ARRANGED,
                            Region.EU,
                            "1.11.1"
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
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.data.TIMESTAMP")
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.data.ID")
            .withEqualsForFields(AssertionUtil::numberEquals, "staticData.data.ID")
            .isEqualTo(REFERENCE_GROUPS.get(groupMode).stream()
                .map(h->new TeamHistory<>(
                    idSupported
                        ? new RawTeamHistoryStaticData(Map.of(
                            StaticColumn.ID,
                            h.staticData().data().get(StaticColumn.ID)))
                        : RawTeamHistoryStaticData.EMPTY,
                    new RawTeamHistoryHistoryData(Map.of(column, h.history().data().get(column)))
                ))
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
        List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> found
            = objectMapper.readValue(mvc.perform(get("/api/team-histories")
                .queryParam
                (
                    "teamLegacyUid",
                    mvcConversionService.convert
                    (
                        new TeamLegacyUid
                        (
                            QueueType.LOTV_1V1,
                            TeamType.ARRANGED,
                            Region.EU,
                            "1.11.1"
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
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.data.TIMESTAMP")
            .withEqualsForFields(AssertionUtil::numberEquals, "staticData.data.ID")
            .isEqualTo(REFERENCE_GROUPS.get(groupMode).stream()
                .map(h-> new TeamHistory<>(
                    new RawTeamHistoryStaticData(Map.of(column, h.staticData().data().get(column))),
                    new RawTeamHistoryHistoryData(Map.of(
                        HistoryColumn.TIMESTAMP,
                        h.history().data().get(HistoryColumn.TIMESTAMP)
                    ))
                ))
                .toList()
            );
    }

    @SuppressWarnings("unchecked")
    public static Stream<Arguments> verifyHistoryParameterValidation()
    {
        return Stream.concat
        (
            Stream.of(Arguments.of(
                "Required parameter 'history' is not present",
                Map.of("teamId", 1L)
            )),
            verifyParameterValidation()
                .peek(args->((Map<String, Object>) args.get()[1])
                    .put("history", HistoryColumn.TIMESTAMP))
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
                new HashMap<String, Object>(Map.of(
                    "teamId", LongStream.range(0, HISTORY_TEAM_COUNT_MAX + 1).toArray()
                ))
            ),
            Arguments.of
            (
                "At least one group id is required",
                new HashMap<String, Object>()
            ),
            Arguments.of
            (
                "'from' parameter must be before 'to' parameter",
                new HashMap<String, Object>(Map.of(
                    "teamId", 1L,
                    "from", now,
                    "to", now.minusSeconds(1)
                ))
            ),
            Arguments.of
            (
                "'from' parameter must be before 'to' parameter",
                new HashMap<String, Object>(Map.of(
                    "teamId", 1L,
                    "from", now,
                    "to", now
                ))
            ),
            Arguments.of
            (
                "Some static columns are not supported by the group mode",
                new HashMap<String, Object>(Map.of(
                    "teamId", 1L,
                    "static", StaticColumn.ID,
                    "groupBy", GroupMode.LEGACY_UID
                ))
            ),
            Arguments.of
            (
                "Some static columns are not supported by the group mode",
                new HashMap<String, Object>(Map.of(
                    "teamId", 1L,
                    "static", StaticColumn.SEASON,
                    "groupBy", GroupMode.LEGACY_UID
                ))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    public void verifyHistoryParameterValidation(String errorFragment, Map<String, Object> parameters)
    throws Exception
    {
        MockHttpServletRequestBuilder req = get("/api/team-histories")
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

    @EnumSource(GroupMode.class)
    @ParameterizedTest
    public void testDefaultFullHistorySummary(GroupMode groupMode)
    throws Exception
    {
        List<TeamHistorySummary<RawTeamHistoryStaticData, RawTeamHistorySummaryData>> found =
            objectMapper.readValue(mvc.perform(get("/api/team-history-summaries")
                .queryParam
                (
                    "teamLegacyUid",
                    mvcConversionService.convert
                    (
                        new TeamLegacyUid
                        (
                            QueueType.LOTV_1V1,
                            TeamType.ARRANGED,
                            Region.EU,
                            "1.11.1"
                        ),
                        String.class
                    )
                )
                .queryParam
                (
                    "summary",
                    Arrays.stream(SummaryColumn.values())
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
        if(found.size() > 1) found.sort(ID_SUMMARY_COMPARATOR);

        Assertions.assertThat(found)
            .usingRecursiveComparison()
            .withEqualsForFields(AssertionUtil::numberEquals, "staticData.data.ID")
            .isEqualTo(REFERENCE_SUMMARY_GROUPS.get(groupMode));
    }

    @SuppressWarnings("unchecked")
    public static Stream<Arguments> testSummaryToAndFromFilters()
    {
        return testToAndFromFilters()
            .map(args->Arguments.of(
                args.get()[0],
                args.get()[1],
                ((List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>>)args.get()[2])
                    .stream()
                    .map(TeamGroupHistoryIT::toSummary)
                    .toList()
            ));
    }

    @ParameterizedTest
    @MethodSource
    public void testSummaryToAndFromFilters
    (
        OffsetDateTime from,
        OffsetDateTime to,
        List<TeamHistorySummary<RawTeamHistoryStaticData, RawTeamHistorySummaryData>> expected
    )
    throws Exception
    {
        List<TeamHistorySummary<RawTeamHistoryStaticData, RawTeamHistorySummaryData>> found =
            objectMapper.readValue(mvc.perform(
                get("/api/team-history-summaries")
                    .queryParam
                    (
                        "teamLegacyUid",
                        mvcConversionService.convert
                        (
                            new TeamLegacyUid
                            (
                                QueueType.LOTV_1V1,
                                TeamType.ARRANGED,
                                Region.EU,
                                "1.11.1"
                            ),
                            String.class
                        )
                    )
                    .queryParam
                    (
                        "summary",
                        Arrays.stream(SummaryColumn.values())
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
                .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        if(found.size() > 1) found.sort(ID_SUMMARY_COMPARATOR);
        Assertions.assertThat(found)
            .usingRecursiveComparison()
            .withEqualsForFields(AssertionUtil::numberEquals, "staticData.data.ID")
            .isEqualTo(expected);
    }

    public static Stream<Arguments> testSingleHistorySummarySummaryColumn()
    {
        return Arrays.stream(GroupMode.values())
            .flatMap(groupMode->Arrays.stream(SummaryColumn.values()).map(c->Arguments.of(c, groupMode)));
    }

    @MethodSource
    @ParameterizedTest
    public void testSingleHistorySummarySummaryColumn(SummaryColumn column, GroupMode groupMode)
    throws Exception
    {
        boolean idSupported = groupMode.isSupported(StaticColumn.ID);
        List<TeamHistorySummary<RawTeamHistoryStaticData, RawTeamHistorySummaryData>> found =
            objectMapper.readValue(mvc.perform(get("/api/team-history-summaries")
                .queryParam
                (
                    "teamLegacyUid",
                    mvcConversionService.convert
                    (
                        new TeamLegacyUid
                        (
                            QueueType.LOTV_1V1,
                            TeamType.ARRANGED,
                            Region.EU,
                            "1.11.1"
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
                    "summary",
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
        if(idSupported) found.sort(ID_SUMMARY_COMPARATOR);

        Assertions.assertThat(found)
            .usingRecursiveComparison()
            .withEqualsForFields(AssertionUtil::numberEquals, "staticData.data.ID")
            .isEqualTo(REFERENCE_SUMMARY_GROUPS.get(groupMode).stream()
                .map(h->new TeamHistorySummary<>(
                    idSupported
                        ? new RawTeamHistoryStaticData(Map.of(
                            StaticColumn.ID,
                            h.staticData().data().get(StaticColumn.ID)))
                        : RawTeamHistoryStaticData.EMPTY,
                    new RawTeamHistorySummaryData(Map.of(column, h.summary().data().get(column)))))
                .toList()
            );
    }

    @MethodSource("testSingleStaticColumn")
    @ParameterizedTest
    public void testSingleSummaryStaticColumn(StaticColumn column, GroupMode groupMode)
    throws Exception
    {
        List<TeamHistorySummary<RawTeamHistoryStaticData, RawTeamHistorySummaryData>> found =
            objectMapper.readValue(mvc.perform(get("/api/team-history-summaries")
                .queryParam
                (
                    "teamLegacyUid",
                    mvcConversionService.convert
                    (
                        new TeamLegacyUid
                        (
                            QueueType.LOTV_1V1,
                            TeamType.ARRANGED,
                            Region.EU,
                            "1.11.1"
                        ),
                        String.class
                    )
                )
                .queryParam
                (
                    "summary",
                    mvcConversionService.convert(SummaryColumn.RATING_MIN, String.class)
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
        found.sort(MIN_RATING_SUMMARY_COMPARATOR);

        Assertions.assertThat(found)
            .usingRecursiveComparison()
            .withEqualsForFields(AssertionUtil::numberEquals, "staticData.data.ID")
            .isEqualTo(REFERENCE_SUMMARY_GROUPS.get(groupMode).stream()
                .map(h->new TeamHistorySummary<>(
                    new RawTeamHistoryStaticData(Map.of(column, h.staticData().data().get(column))),
                    new RawTeamHistorySummaryData(Map.of(
                        SummaryColumn.RATING_MIN,
                        h.summary().data().get(SummaryColumn.RATING_MIN)))))
                .toList()
            );
    }

    @SuppressWarnings("unchecked")
    public static Stream<Arguments> verifyHistorySummaryParameterValidation()
    {
        return Stream.concat
        (
            Stream.of(Arguments.of(
                "Required parameter 'summary' is not present",
                Map.of("teamId", 1L)
            )),
            verifyParameterValidation()
                .peek(args->((Map<String, Object>) args.get()[1])
                    .put("summary", SummaryColumn.GAMES))
        );
    }

    @ParameterizedTest
    @MethodSource
    public void verifyHistorySummaryParameterValidation
    (
        String errorFragment,
        Map<String, Object> parameters
    )
    throws Exception
    {
        MockHttpServletRequestBuilder req = get("/api/team-history-summaries")
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

    public static <T extends Number> T convert(Object val, Function<Number, T> converter)
    {
        return val == null ? null : converter.apply((Number) val);
    }

    public static <T extends Number> List<T> convert(List<?> vals, Function<Number, T> converter)
    {
        return vals.stream()
            .map(v->(Number) v)
            .map(n->n == null ? null : converter.apply(n))
            .toList();
    }

    @Test
    public void testHistoryConversion()
    {
        List<TeamHistory<ConvertedTeamHistoryStaticData, ConvertedTeamHistoryHistoryData>> converted
            = teamHistoryDAO.find
            (
                Set.of(13L),
                null, null,
                EnumSet.allOf(StaticColumn.class), EnumSet.allOf(HistoryColumn.class),
                GroupMode.TEAM
            ).stream()
                .map(TeamHistory::cast)
                .map(typed->TeamHistory.convert(typed, sc2ConversionService))
                .toList();
        Map<HistoryColumn, List<?>> data = FULL_HISTORY.stream()
            .filter(h->h.staticData().data().get(StaticColumn.ID).equals(13L))
            .findAny()
            .orElseThrow()
            .history()
            .data();
        Assertions.assertThat(converted)
            .usingRecursiveComparison()
            .isEqualTo(List.of(
                new TeamHistory<>
                (
                    new ConvertedTeamHistoryStaticData
                    (
                        13L,
                        Region.EU,
                        QueueType.LOTV_1V1,
                        TeamType.ARRANGED,
                        2,
                        TeamLegacyId.trusted("1.11.1")
                    ),
                    new ConvertedTeamHistoryHistoryData
                    (
                        convert(data.get(HistoryColumn.TIMESTAMP), Number::longValue),
                        convert(data.get(HistoryColumn.RATING), Number::intValue),
                        convert(data.get(HistoryColumn.GAMES), Number::intValue),
                        convert(data.get(HistoryColumn.WINS), Number::intValue),
                        List.of
                        (
                            BaseLeague.LeagueType.BRONZE,
                            BaseLeague.LeagueType.MASTER,
                            BaseLeague.LeagueType.SILVER
                        ),
                        List.of
                        (
                            BaseLeagueTier.LeagueTierType.FIRST,
                            BaseLeagueTier.LeagueTierType.SECOND,
                            BaseLeagueTier.LeagueTierType.FIRST
                        ),
                        convert(data.get(HistoryColumn.DIVISION_ID), Number::intValue),
                        convert(data.get(HistoryColumn.GLOBAL_RANK), Number::intValue),
                        convert(data.get(HistoryColumn.REGION_RANK), Number::intValue),
                        convert(data.get(HistoryColumn.LEAGUE_RANK), Number::intValue),
                        convert(data.get(HistoryColumn.GLOBAL_TEAM_COUNT), Number::intValue),
                        convert(data.get(HistoryColumn.REGION_TEAM_COUNT), Number::intValue),
                        convert(data.get(HistoryColumn.LEAGUE_TEAM_COUNT), Number::intValue),
                        convert(data.get(HistoryColumn.ID), Number::longValue),
                        convert(data.get(HistoryColumn.SEASON), Number::intValue)
                    )
                )
            ));
    }
    
    @Test
    public void testHistorySummaryConversion()
    {
        List<TeamHistorySummary<ConvertedTeamHistoryStaticData, TypedTeamHistorySummaryData>> converted
            = teamHistoryDAO.findSummary
            (
                Set.of(13L),
                null, null,
                EnumSet.allOf(StaticColumn.class), EnumSet.allOf(SummaryColumn.class),
                GroupMode.TEAM
            ).stream()
                .map(TeamHistorySummary::cast)
                .map(typed->TeamHistorySummary.convert(typed, sc2ConversionService))
                .toList();
        Map<SummaryColumn, ?> data = REFERENCE_SUMMARY_GROUPS.get(GroupMode.TEAM).stream()
            .filter(h->h.staticData().data().get(StaticColumn.ID).equals(13L))
            .findAny()
            .orElseThrow()
            .summary()
            .data();
        Assertions.assertThat(converted)
            .usingRecursiveComparison()
            .isEqualTo(List.of(
                new TeamHistorySummary<>
                (
                    new ConvertedTeamHistoryStaticData
                    (
                        13L,
                        Region.EU,
                        QueueType.LOTV_1V1,
                        TeamType.ARRANGED,
                        2,
                        TeamLegacyId.trusted("1.11.1")
                    ),
                    new TypedTeamHistorySummaryData
                    (
                        convert(data.get(SummaryColumn.GAMES), Number::intValue),
                        convert(data.get(SummaryColumn.RATING_MIN), Number::intValue),
                        convert(data.get(SummaryColumn.RATING_AVG), Number::doubleValue),
                        convert(data.get(SummaryColumn.RATING_MAX), Number::intValue),
                        convert(data.get(SummaryColumn.RATING_LAST), Number::intValue),
                        convert(data.get(SummaryColumn.REGION_RANK_LAST), Number::intValue),
                        convert(data.get(SummaryColumn.REGION_TEAM_COUNT_LAST), Number::intValue)
                    )
                )
            ));
    }

}
