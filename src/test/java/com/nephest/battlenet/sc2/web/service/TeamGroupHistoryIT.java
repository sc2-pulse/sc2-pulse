// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static java.util.Map.entry;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clickhouse.client.api.Client;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Division;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.dao.DivisionDAO;
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
import com.nephest.battlenet.sc2.model.local.inner.TeamHistoryDAO.HistoryColumn;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistoryDAO.StaticColumn;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistoryDAO.SummaryColumn;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistorySummary;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyId;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyIdEntry;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import com.nephest.battlenet.sc2.model.local.inner.TypedTeamHistorySummaryData;
import com.nephest.battlenet.sc2.model.util.DbTestUtil;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.util.AssertionUtil;
import com.nephest.battlenet.sc2.web.controller.group.TeamGroupArgumentResolver;
import com.nephest.battlenet.sc2.web.util.TeamLegacyUidValidationUtil;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(classes = AllTestConfig.class)
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TeamGroupHistoryIT
{

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

    private static List<Season> seasons;
    private static List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> FULL_HISTORY;
    private static List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> FULL_HISTORY_LEGACY_UID_GROUP;
    private static List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> FULL_HISTORY_LEGACY_UID_GROUP_PLAYER_ACTIONS;
    private static List<TeamHistorySummary<RawTeamHistoryStaticData, RawTeamHistorySummaryData>> FULL_SUMMARY_LEGACY_UID_GROUP;

    private static TeamLegacyUid teamLegacyUid;
    private static String teamLegacyUidString;

    @BeforeAll
    public static void beforeAll
    (
        @Autowired DataSource dataSource,
        @Autowired Client clickHouseCLient,
        @Autowired DivisionDAO divisionDAO,
        @Autowired TeamDAO teamDAO,
        @Autowired TeamStateDAO teamStateDAO,
        @Autowired TeamHistoryDAO teamHistoryDAO,
        @Autowired PopulationStateDAO populationStateDAO,
        @Autowired LeagueStatsDAO leagueStatsDAO,
        @Autowired SeasonGenerator seasonGenerator,
        @Autowired JdbcTemplate jdbcTemplate,
        @Autowired @Qualifier("mvcConversionService") ConversionService mvcConversionService
    )
    throws Exception
    {
        DbTestUtil.initDb(dataSource, clickHouseCLient);
        teamLegacyUid = new TeamLegacyUid
        (
            QueueType.LOTV_1V1,
            TeamType.ARRANGED,
            Region.EU,
            "1.11.1"
        );
        teamLegacyUidString = mvcConversionService.convert(teamLegacyUid, String.class);
        init
        (
            divisionDAO,
            teamDAO,
            teamStateDAO,
            teamHistoryDAO,
            populationStateDAO,
            leagueStatsDAO,
            seasonGenerator,
            jdbcTemplate,
            mvcConversionService
        );
    }

    @AfterAll
    public static void afterAll(@Autowired DataSource dataSource, @Autowired Client clickHouseClient)
    throws Exception
    {
        DbTestUtil.clearDb(dataSource, clickHouseClient);
    }

    private static void init
    (
        DivisionDAO divisionDAO,
        TeamDAO teamDAO,
        TeamStateDAO teamStateDAO,
        TeamHistoryDAO teamHistoryDAO,
        PopulationStateDAO populationStateDAO,
        LeagueStatsDAO leagueStatsDAO,
        SeasonGenerator seasonGenerator,
        JdbcTemplate jdbcTemplate,
        ConversionService mvcConversionService
    )
    throws Exception
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
        teamHistoryDAO.trySync();
        Map<Integer, Long> divisionIdMap = divisionDAO.findByIds(Set.of(1, 5, 15, 7, 9)).stream()
            .collect(Collectors.toMap(Division::getId, Division::getBattlenetId));

        FULL_HISTORY = getFullTeamHistory(mvcConversionService, divisionIdMap);
        FULL_HISTORY_LEGACY_UID_GROUP = List.of(new TeamHistory<>
        (
            new RawTeamHistoryStaticData(Map.of(
                StaticColumn.LEGACY_UID,
                teamLegacyUidString
            )),
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
        FULL_HISTORY_LEGACY_UID_GROUP_PLAYER_ACTIONS = FULL_HISTORY_LEGACY_UID_GROUP.stream()
            .map(h->new TeamHistory<>(
                h.staticData(),
                new RawTeamHistoryHistoryData(playerActionsOnly(h.history().data()))
            ))
            .toList();
        FULL_SUMMARY_LEGACY_UID_GROUP = FULL_HISTORY_LEGACY_UID_GROUP_PLAYER_ACTIONS.stream()
            .map(TeamGroupHistoryIT::toSummary)
            .toList();
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
        List<Integer> rating = mapValues(history.get(HistoryColumn.RATING), Number::intValue).toList();
        if(rating.isEmpty()) return RawTeamHistorySummaryData.EMPTY;

        List<Integer> season = mapValues(history.get(HistoryColumn.SEASON), Number::intValue).toList();
        List<Integer> games = mapValues(history.get(HistoryColumn.GAMES), Number::intValue).toList();
        Map<SummaryColumn, Object> summary = new EnumMap<>(SummaryColumn.class);
        summary.put(SummaryColumn.GAMES, calculateGames(season, rating, games));
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
        List<Integer> season = mapValues(history.get(HistoryColumn.SEASON), Number::intValue).toList();
        List<Integer> games = mapValues(history.get(HistoryColumn.GAMES), Number::intValue).toList();
        List<Integer> validIx = IntStream.range(0, rating.size())
            .filter(i->i == 0
                || !games.get(i).equals(games.get(i - 1))
                || !rating.get(i).equals(rating.get(i - 1))
                || !season.get(i).equals(season.get(i - 1)))
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
        List<Integer> season,
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
                : !season.get(i).equals(season.get(prevI))
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
        ConversionService conversionService,
        Map<Integer, Long> divisionIdMap
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

                    entry
                    (
                        TeamHistoryDAO.HistoryColumn.DIVISION_BATTLENET_ID,
                        Stream.of(1, 1, 1).map(divisionIdMap::get).toList()
                    ),
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

                    entry(HistoryColumn.SEASON, List.of(1, 1, 1))
                ))
            ),
            new TeamHistory<>
            (
                new RawTeamHistoryStaticData(Map.of(
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

                    entry
                    (
                        HistoryColumn.DIVISION_BATTLENET_ID,
                        Stream.of(5, 15, 7).map(divisionIdMap::get).toList()
                    ),
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

                    entry(HistoryColumn.SEASON, List.of(2, 2, 2))
                ))
            ),
            new TeamHistory<>
            (
                new RawTeamHistoryStaticData(Map.of(
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

                    entry
                    (
                        TeamHistoryDAO.HistoryColumn.DIVISION_BATTLENET_ID,
                        Stream.of(9, 9).map(divisionIdMap::get).toList()
                    ),
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

                    entry(HistoryColumn.SEASON, List.of(3, 3))
                ))
            )
        );
    }

    @Test
    public void testDefaultFullHistory()
    throws Exception
    {
        List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> found
            = objectMapper.readValue(mvc.perform(asyncDispatch(mvc.perform(get("/api/team-histories")
                .queryParam("teamLegacyUid", teamLegacyUidString)
                .queryParam
                (
                    "history",
                    Arrays.stream(HistoryColumn.values())
                        .map(c->mvcConversionService.convert(c, String.class))
                        .toArray(String[]::new)
                )
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(request().asyncStarted()).andReturn()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        Assertions.assertThat(found)
            .usingRecursiveComparison()
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.data.TIMESTAMP")
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.data.DIVISION_BATTLENET_ID")
            .isEqualTo(FULL_HISTORY_LEGACY_UID_GROUP);
    }

    @SuppressWarnings("unchecked")
    public static TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData> filterByFromAndTo
    (
        TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData> history,
        OffsetDateTime from,
        OffsetDateTime to
    )
    {
        Long fromSeconds = from == null ? Long.MIN_VALUE : from.toEpochSecond();
        Long toSeconds = to == null ? Long.MAX_VALUE : to.toEpochSecond();
        List<Long> timestamps =
            (List<Long>) history.history().data().get(HistoryColumn.TIMESTAMP);
        List<Integer> indexes = IntStream.range(0, timestamps.size())
            .filter(ix->timestamps.get(ix) >= fromSeconds && timestamps.get(ix) < toSeconds)
            .boxed()
            .toList();
        if(indexes.isEmpty()) return null;

        return new TeamHistory<>
        (
            history.staticData(),
            new RawTeamHistoryHistoryData
            (
                history.history().data().entrySet().stream()
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e->indexes.stream()
                            .map(ix->e.getValue().get(ix))
                            .toList()
                    ))
            )
        );
    }

    public static Stream<Arguments> testToAndFromFilters()
    {
        return Stream.of
        (
            Arguments.of
            (
                seasons.get(0).getStart().plusMinutes(1),
                null
            ),
            Arguments.of
            (
                seasons.get(1).getStart().plusMinutes(1),
                null
            ),
            Arguments.of
            (
                seasons.get(2).getStart().plusMinutes(1),
                null
            ),

            Arguments.of
            (
                seasons.get(1).getStart().plusMinutes(2),
                null
            ),
            Arguments.of
            (
                seasons.get(1).getStart().plusMinutes(3),
                null
            ),

            Arguments.of
            (
                null,
                seasons.get(1).getStart().plusSeconds(1)
            ),
            Arguments.of
            (
                null,
                seasons.get(0).getStart().plusMinutes(3)
            ),
            Arguments.of
            (
                null,
                seasons.get(0).getStart().plusMinutes(2)
            ),

            Arguments.of
            (
                seasons.get(0).getStart().plusMinutes(3),
                seasons.get(2).getStart().plusMinutes(2)
            ),

            Arguments.of
            (
                seasons.get(seasons.size() - 1).getEnd(),
                null
            ),
            Arguments.of
            (
                null,
                seasons.get(0).getStart()
            )
        )
            .map(args->{
                OffsetDateTime from = (OffsetDateTime) args.get()[0];
                OffsetDateTime to = (OffsetDateTime) args.get()[1];
                return Arguments.of
                (
                    from, to,
                    FULL_HISTORY_LEGACY_UID_GROUP.stream()
                        .map(h->filterByFromAndTo(h, from , to))
                        .filter(Objects::nonNull)
                        .toList()
                );
            });
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
            objectMapper.readValue(mvc.perform(asyncDispatch(mvc.perform(get("/api/team-histories")
                    .queryParam("teamLegacyUid", teamLegacyUidString)
                    .queryParam
                    (
                        "history",
                        Arrays.stream(HistoryColumn.values())
                            .map(c->mvcConversionService.convert(c, String.class))
                            .toArray(String[]::new)
                    )
                    .queryParam("from", mvcConversionService.convert(from, String.class))
                    .queryParam("to", mvcConversionService.convert(to, String.class))
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(request().asyncStarted()).andReturn()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        Assertions.assertThat(found)
            .usingRecursiveComparison()
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.data.TIMESTAMP")
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.data.DIVISION_BATTLENET_ID")
            .isEqualTo(expected);
    }

    @EnumSource(HistoryColumn.class)
    @ParameterizedTest
    public void testSingleHistoryColumn(HistoryColumn column)
    throws Exception
    {
        List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> found
            = objectMapper.readValue(mvc.perform(asyncDispatch(mvc.perform(get("/api/team-histories")
                .queryParam("teamLegacyUid", teamLegacyUidString)
                .queryParam
                (
                    "history",
                    mvcConversionService.convert(column, String.class)
                )
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(request().asyncStarted()).andReturn()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        Assertions.assertThat(found)
            .usingRecursiveComparison()
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.data.TIMESTAMP")
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.data.DIVISION_BATTLENET_ID")
            .isEqualTo(FULL_HISTORY_LEGACY_UID_GROUP.stream()
                .map(h->new TeamHistory<>(
                    new RawTeamHistoryStaticData(Map.of(
                        TeamHistoryDAO.StaticColumn.LEGACY_UID,
                        teamLegacyUidString
                    )),
                    new RawTeamHistoryHistoryData(Map.of(column, h.history().data().get(column)))
                ))
                .toList()
            );
    }

    @SuppressWarnings("unchecked")
    public Stream<Arguments> verifyHistoryParameterValidation()
    {
        return Stream.concat
        (
            Stream.of(Arguments.of(
                "Required parameter 'history' is not present",
                Map.of("teamLegacyUid", teamLegacyUidString)
            )),
            verifyParameterValidation()
                .peek(args->((Map<String, Object>) args.get()[1])
                    .put("history", HistoryColumn.TIMESTAMP))
        );
    }

    public Stream<Arguments> verifyParameterValidation()
    {
        OffsetDateTime now = SC2Pulse.offsetDateTime();
        Stream<Arguments> localArgs = Stream.of
        (
            Arguments.of
            (
                "Validation failure",
                new HashMap<String, Object>(Map.of(
                    "teamLegacyUid",
                    LongStream.range(0, TeamGroupArgumentResolver.LEGACY_UIDS_MAX + 1)
                        .mapToObj(i->new TeamLegacyUid(
                            QueueType.LOTV_1V1,
                            TeamType.ARRANGED,
                            Region.EU,
                            TeamLegacyId.standard(List.of(
                                new TeamLegacyIdEntry(1, i, Race.TERRAN)
                            ))
                        ))
                        .map(uid->mvcConversionService.convert(uid, String.class))
                        .toArray()
                ))
            ),
            Arguments.of
            (
                "Required parameter 'teamLegacyUid' is not present.",
                new HashMap<String, Object>()
            ),
            Arguments.of
            (
                "'from' parameter must be before 'to' parameter",
                new HashMap<String, Object>(Map.of(
                    "teamLegacyUid", teamLegacyUidString,
                    "from", now,
                    "to", now.minusSeconds(1)
                ))
            ),
            Arguments.of
            (
                "'from' parameter must be before 'to' parameter",
                new HashMap<String, Object>(Map.of(
                    "teamLegacyUid", teamLegacyUidString,
                    "from", now,
                    "to", now
                ))
            )
        );
        return Stream.concat
        (
            localArgs,
            TeamLegacyUidValidationUtil.invalidTeamLegacyUidValidationArgs()
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

    @Test
    public void testDefaultFullHistorySummary()
    throws Exception
    {
        List<TeamHistorySummary<RawTeamHistoryStaticData, RawTeamHistorySummaryData>> found =
            objectMapper.readValue(mvc.perform(get("/api/team-history-summaries")
                .queryParam("teamLegacyUid", teamLegacyUidString)
                .queryParam
                (
                    "summary",
                    Arrays.stream(SummaryColumn.values())
                        .map(c->mvcConversionService.convert(c, String.class))
                        .toArray(String[]::new)
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        Assertions.assertThat(found)
            .usingRecursiveComparison()
            .isEqualTo(FULL_SUMMARY_LEGACY_UID_GROUP);
    }

    public Stream<Arguments> testSummaryToAndFromFilters()
    {
        return testToAndFromFilters()
            .map(args->{
                OffsetDateTime from = (OffsetDateTime) args.get()[0];
                OffsetDateTime to = (OffsetDateTime) args.get()[1];
                return Arguments.of
                (
                    from, to,
                    FULL_HISTORY_LEGACY_UID_GROUP_PLAYER_ACTIONS.stream()
                        .map(h->filterByFromAndTo(h, from , to))
                        .filter(Objects::nonNull)
                        .map(TeamGroupHistoryIT::toSummary)
                        .toList()
                );
            });
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
                    .queryParam("teamLegacyUid", teamLegacyUidString)
                    .queryParam
                    (
                        "summary",
                        Arrays.stream(SummaryColumn.values())
                            .map(c->mvcConversionService.convert(c, String.class))
                            .toArray(String[]::new)
                    )
                    .queryParam("from", mvcConversionService.convert(from, String.class))
                    .queryParam("to", mvcConversionService.convert(to, String.class))
                    .contentType(MediaType.APPLICATION_JSON)
            )
                .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        Assertions.assertThat(found)
            .usingRecursiveComparison()
            .isEqualTo(expected);
    }

    @EnumSource(SummaryColumn.class)
    @ParameterizedTest
    public void testSingleHistorySummarySummaryColumn(SummaryColumn column)
    throws Exception
    {
        List<TeamHistorySummary<RawTeamHistoryStaticData, RawTeamHistorySummaryData>> found =
            objectMapper.readValue(mvc.perform(get("/api/team-history-summaries")
                .queryParam("teamLegacyUid", teamLegacyUidString)
                .queryParam
                (
                    "summary",
                    mvcConversionService.convert(column, String.class)
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        Assertions.assertThat(found)
            .usingRecursiveComparison()
            .isEqualTo(FULL_SUMMARY_LEGACY_UID_GROUP.stream()
                .map(h -> {
                    Map<SummaryColumn, Object> summaryData = new EnumMap<>(SummaryColumn.class);
                    summaryData.put(column, h.summary().data().get(column));
                    return new TeamHistorySummary<>
                    (
                        new RawTeamHistoryStaticData(Map.of(
                            StaticColumn.LEGACY_UID,
                            h.staticData().data().get(StaticColumn.LEGACY_UID)
                        )),
                        new RawTeamHistorySummaryData(summaryData)
                    );
                })
                .toList()
            );
    }

    @SuppressWarnings("unchecked")
    public Stream<Arguments> verifyHistorySummaryParameterValidation()
    {
        return Stream.concat
        (
            Stream.of(Arguments.of(
                "Required parameter 'summary' is not present",
                Map.of("teamLegacyUid", teamLegacyUidString)
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
    throws Exception
    {
        List<TeamHistory<RawTeamHistoryStaticData, RawTeamHistoryHistoryData>> found
            = objectMapper.readValue(mvc.perform(asyncDispatch(mvc.perform(get("/api/team-histories")
                .queryParam("teamLegacyUid", teamLegacyUidString)
                .queryParam
                (
                    "history",
                    Arrays.stream(HistoryColumn.values())
                        .map(c->mvcConversionService.convert(c, String.class))
                        .toArray(String[]::new)
                )
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(request().asyncStarted()).andReturn()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});
        List<TeamHistory<ConvertedTeamHistoryStaticData, ConvertedTeamHistoryHistoryData>> converted
            = found.stream()
                .map(TeamHistory::cast)
                .map(typed->TeamHistory.convert(typed, sc2ConversionService))
                .toList();
        Map<HistoryColumn, List<?>> data = FULL_HISTORY_LEGACY_UID_GROUP.get(0).history().data();
        Assertions.assertThat(converted)
            .usingRecursiveComparison()
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.timestamps")
            .withEqualsForFields(AssertionUtil::numberListEquals,"history.divisionBattlenetIds")
            .isEqualTo(List.of(
                new TeamHistory<>
                (
                    new ConvertedTeamHistoryStaticData(teamLegacyUid),
                    new ConvertedTeamHistoryHistoryData
                    (
                        convert(data.get(HistoryColumn.TIMESTAMP), Number::longValue),
                        convert(data.get(HistoryColumn.RATING), Number::intValue),
                        convert(data.get(HistoryColumn.GAMES), Number::intValue),
                        convert(data.get(HistoryColumn.WINS), Number::intValue),
                        data.get(HistoryColumn.LEAGUE_TYPE).stream()
                            .map(l->sc2ConversionService.convert(l, BaseLeague.LeagueType.class))
                            .toList(),
                        data.get(HistoryColumn.TIER_TYPE).stream()
                            .map(l->sc2ConversionService.convert(l, BaseLeagueTier.LeagueTierType.class))
                            .toList(),
                        convert(data.get(HistoryColumn.DIVISION_BATTLENET_ID), Number::longValue),
                        convert(data.get(HistoryColumn.GLOBAL_RANK), Number::intValue),
                        convert(data.get(HistoryColumn.REGION_RANK), Number::intValue),
                        convert(data.get(HistoryColumn.LEAGUE_RANK), Number::intValue),
                        convert(data.get(HistoryColumn.GLOBAL_TEAM_COUNT), Number::intValue),
                        convert(data.get(HistoryColumn.REGION_TEAM_COUNT), Number::intValue),
                        convert(data.get(HistoryColumn.LEAGUE_TEAM_COUNT), Number::intValue),
                        convert(data.get(HistoryColumn.SEASON), Number::intValue)
                    )
                )
            ));
    }
    
    @Test
    public void testHistorySummaryConversion()
    throws Exception
    {
        List<TeamHistorySummary<RawTeamHistoryStaticData, RawTeamHistorySummaryData>> found =
            objectMapper.readValue(mvc.perform(get("/api/team-history-summaries")
                .queryParam("teamLegacyUid", teamLegacyUidString)
                .queryParam
                (
                    "summary",
                    Arrays.stream(SummaryColumn.values())
                        .map(c->mvcConversionService.convert(c, String.class))
                        .toArray(String[]::new)
                )
                .contentType(MediaType.APPLICATION_JSON)
            )
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});

        List<TeamHistorySummary<ConvertedTeamHistoryStaticData, TypedTeamHistorySummaryData>> converted
            = found.stream()
                .map(TeamHistorySummary::cast)
                .map(typed->TeamHistorySummary.convert(typed, sc2ConversionService))
                .toList();
        Map<SummaryColumn, ?> data = FULL_SUMMARY_LEGACY_UID_GROUP.get(0).summary().data();
        Assertions.assertThat(converted)
            .usingRecursiveComparison()
            .isEqualTo(List.of(
                new TeamHistorySummary<>
                (
                    new ConvertedTeamHistoryStaticData(teamLegacyUid),
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
