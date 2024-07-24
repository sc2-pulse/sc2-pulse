// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static com.nephest.battlenet.sc2.model.BaseLeague.LeagueType.SILVER;
import static com.nephest.battlenet.sc2.model.BaseLeagueTier.LeagueTierType.THIRD;
import static com.nephest.battlenet.sc2.model.QueueType.LOTV_1V1;
import static com.nephest.battlenet.sc2.model.Race.PROTOSS;
import static com.nephest.battlenet.sc2.model.Race.TERRAN;
import static com.nephest.battlenet.sc2.model.Race.ZERG;
import static com.nephest.battlenet.sc2.model.TeamType.ARRANGED;
import static com.nephest.battlenet.sc2.web.service.MapService.FILM_FRAME_DURATION;
import static com.nephest.battlenet.sc2.web.service.MapStatsFilmTestService.FRAME_NUMBER;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.LeagueTier;
import com.nephest.battlenet.sc2.model.local.MapStatsFilm;
import com.nephest.battlenet.sc2.model.local.MapStatsFilmSpec;
import com.nephest.battlenet.sc2.model.local.MapStatsFrame;
import com.nephest.battlenet.sc2.model.local.MatchUp;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.LeagueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.MapStatsFilmSpecDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchParticipantDAO;
import com.nephest.battlenet.sc2.model.local.dao.PopulationStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMapStatsFilm;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.service.EventService;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
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

@SpringBootTest(classes = AllTestConfig.class)
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class MapStatsFilmCrossTierIT
{

    @Autowired @Qualifier("mvcConversionService")
    private ConversionService mvcConversionService;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static Map<Integer, MapStatsFilmSpec> specsMap;
    private static MapStatsFilmSpec pvtSpec;
    private static MapStatsFilmSpec tvzSpec;

    @BeforeAll
    public static void beforeAll
    (
        @Autowired DataSource dataSource,
        @Autowired MapService mapService,
        @Autowired SeasonGenerator seasonGenerator,
        @Autowired LeagueStatsDAO leagueStatsDAO,
        @Autowired PopulationStateDAO populationStateDAO,
        @Autowired TeamDAO teamDAO,
        @Autowired JdbcTemplate jdbcTemplate,
        @Autowired TeamStateDAO teamStateDAO,
        @Autowired MatchDAO matchDAO,
        @Autowired MatchParticipantDAO matchParticipantDAO,
        @Autowired EventService eventService,
        @Autowired MapStatsFilmSpecDAO mapStatsFilmSpecDAO,
        @Autowired MapStatsFilmTestService mapStatsFilmTestService
    )
    throws SQLException, InterruptedException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        mapService.getMapStatsInstant().setValue(SC2Pulse.instant());
        mapService.setDbInitialized(false);
        mapStatsFilmTestService.generateFilms((startFrom)->{
            //tvp, b1, s3
            seasonGenerator.createMatch(29L, 46L, startFrom,
                MapStatsFilmTestService.FRAME_OFFSET);
            //tvp, s2, s3, protoss wins
            seasonGenerator.createMatch(50L, 161L, startFrom.plusSeconds(2),
                MapStatsFilmTestService.FRAME_OFFSET);
            //tvz, b1
            seasonGenerator.createMatch(33L, 31L, startFrom.plusSeconds(1),
                MapStatsFilmTestService.FRAME_OFFSET);
            return List.of(29L, 46L, 33L, 31L, 161L, 50L);
        });
        List<MapStatsFilmSpec> specs = mapStatsFilmSpecDAO
            .find(MapService.MATCH_UPS, FILM_FRAME_DURATION);
        specsMap = specs.stream()
            .collect(Collectors.toMap(MapStatsFilmSpec::getId, Function.identity()));
        pvtSpec = specs.stream()
            .filter(spec->spec.getRace() == PROTOSS && spec.getVersusRace() == TERRAN)
            .findAny()
            .orElseThrow();
        tvzSpec = specs.stream()
            .filter(spec->spec.getRace() == TERRAN && spec.getVersusRace() == ZERG)
            .findAny()
            .orElseThrow();
    }

    @AfterAll
    public static void afterAll(@Autowired DataSource dataSource, @Autowired MapService mapService)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
        mapService.getMapStatsInstant().setValue(SC2Pulse.instant());
        mapService.setDbInitialized(false);
    }

    private static Map<MatchUp, Integer> getFilmIds(LadderMapStatsFilm ladderMapStatsFilm)
    {
        return ladderMapStatsFilm.getFilms().values().stream()
            .map(film->{
                MapStatsFilmSpec spec = ladderMapStatsFilm
                    .getSpecs().get(film.getMapStatsFilmSpecId());
                return Map.entry(new MatchUp(spec.getRace(), spec.getVersusRace()), film.getId());
            })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static Stream<Arguments> testCrossTierStats()
    {
        return Stream.of
        (
            Arguments.of
            (
                BaseLeague.LeagueType.BRONZE,
                BaseLeagueTier.LeagueTierType.FIRST,
                Set.of(true, false),
                (Function<LadderMapStatsFilm, LadderMapStatsFilm>) film->
                {
                    Map<MatchUp, Integer> filmIds = getFilmIds(film);
                    int tvzId = filmIds.get(new MatchUp(TERRAN, ZERG));
                    int pvtId = filmIds.get(new MatchUp(PROTOSS, TERRAN));
                    return new LadderMapStatsFilm
                    (
                        Map.of(1, SeasonGenerator.defaultMap()),
                        Map.of(1, SeasonGenerator.defaultSeason()),
                        Map.of(1, SeasonGenerator.defaultLeague()),
                        Map.of(1, SeasonGenerator.defaultTier()),
                        specsMap,
                        Map.of
                        (
                            tvzId, new MapStatsFilm(tvzId, 1, 1, tvzSpec.getId(), false),
                            pvtId, new MapStatsFilm(pvtId, 1, 1, pvtSpec.getId(), true)
                        ),
                        Stream.of
                        (
                            new MapStatsFrame(tvzId, FRAME_NUMBER, 1, 1),
                            new MapStatsFrame(tvzId, null, 1, 1),
                            new MapStatsFrame(pvtId, FRAME_NUMBER, 0, 1),
                            new MapStatsFrame(pvtId, null, 0, 1)
                        )
                            .sorted(MapStatsFrame.NATURAL_ID_COMPARATOR)
                            .toList()
                    );
                }
            ),
            Arguments.of
            (
                BaseLeague.LeagueType.BRONZE,
                BaseLeagueTier.LeagueTierType.FIRST,
                Set.of(false),
                (Function<LadderMapStatsFilm, LadderMapStatsFilm>) film->
                {
                    Map<MatchUp, Integer> filmIds = getFilmIds(film);
                    int tvzId = filmIds.get(new MatchUp(TERRAN, ZERG));
                    return new LadderMapStatsFilm
                    (
                        Map.of(1, SeasonGenerator.defaultMap()),
                        Map.of(1, SeasonGenerator.defaultSeason()),
                        Map.of(1, SeasonGenerator.defaultLeague()),
                        Map.of(1, SeasonGenerator.defaultTier()),
                        specsMap,
                        Map.of(tvzId, new MapStatsFilm(tvzId, 1, 1, tvzSpec.getId(), false)),
                        Stream.of
                        (
                            new MapStatsFrame(tvzId, FRAME_NUMBER, 1, 1),
                            new MapStatsFrame(tvzId, null, 1, 1)
                        )
                            .sorted(MapStatsFrame.NATURAL_ID_COMPARATOR)
                            .toList()
                    );
                }
            ),
            Arguments.of
            (
                BaseLeague.LeagueType.BRONZE,
                BaseLeagueTier.LeagueTierType.FIRST,
                Set.of(true),
                (Function<LadderMapStatsFilm, LadderMapStatsFilm>) film->
                {
                    Map<MatchUp, Integer> filmIds = getFilmIds(film);
                    int pvtId = filmIds.get(new MatchUp(PROTOSS, TERRAN));
                    return new LadderMapStatsFilm
                    (
                        Map.of(1, SeasonGenerator.defaultMap()),
                        Map.of(1, SeasonGenerator.defaultSeason()),
                        Map.of(1, SeasonGenerator.defaultLeague()),
                        Map.of(1, SeasonGenerator.defaultTier()),
                        specsMap,
                        Map.of(pvtId, new MapStatsFilm(pvtId, 1, 1, pvtSpec.getId(), true)),
                        Stream.of
                        (
                            new MapStatsFrame(pvtId, FRAME_NUMBER, 0, 1),
                            new MapStatsFrame(pvtId, null, 0, 1)
                        )
                            .sorted(MapStatsFrame.NATURAL_ID_COMPARATOR)
                            .toList()
                    );
                }
            ),
            Arguments.of
            (
                SILVER,
                THIRD,
                Set.of(true, false),
                (Function<LadderMapStatsFilm, LadderMapStatsFilm>) film->
                {
                    Map<MatchUp, Integer> filmIds = getFilmIds(film);
                    int pvtId = filmIds.get(new MatchUp(PROTOSS, TERRAN));
                    return new LadderMapStatsFilm
                    (
                        Map.of(1, SeasonGenerator.defaultMap()),
                        Map.of(1, SeasonGenerator.defaultSeason()),
                        Map.of(88, new League(88, 1, SILVER, LOTV_1V1, ARRANGED)),
                        Map.of(264, new LeagueTier(264, 88, THIRD, 0, 0)),
                        specsMap,
                        Map.of
                        (
                            pvtId,
                            new MapStatsFilm(pvtId, 1, 264, pvtSpec.getId(), true)
                        ),
                        Stream.of
                        (
                            new MapStatsFrame(pvtId, FRAME_NUMBER, 1, 2),
                            new MapStatsFrame(pvtId, null, 1, 2)
                        )
                            .sorted(MapStatsFrame.NATURAL_ID_COMPARATOR)
                            .toList()
                    );
                }
            )
        );
    }

    @MethodSource
    @ParameterizedTest
    public void testCrossTierStats
    (
        BaseLeague.LeagueType league,
        BaseLeagueTier.LeagueTierType tier,
        Set<Boolean> crossTier,
        Function<LadderMapStatsFilm, LadderMapStatsFilm> expectedResultFunction
    )
    throws Exception
    {
        LadderMapStatsFilm ladderMapStatsFilm = objectMapper.readValue
        (
            mvc.perform
            (
                get("/api/ladder/stats/map/film")
                    .queryParam("season", String.valueOf(SeasonGenerator.DEFAULT_SEASON_ID))
                    .queryParam
                    (
                        "queue",
                        mvcConversionService.convert(LOTV_1V1, String.class)
                    )
                    .queryParam
                    (
                        "teamType",
                        mvcConversionService.convert(ARRANGED, String.class)
                    )
                    .queryParam
                    (
                        "league",
                        mvcConversionService.convert(league, String.class)
                    )
                    .queryParam
                    (
                        "tier",
                        mvcConversionService.convert(tier, String.class)
                    )
                    .queryParam
                    (
                        "crossTier", crossTier.stream()
                            .map(ct->mvcConversionService.convert(ct, String.class))
                            .toArray(String[]::new)
                    )
                    .queryParam
                    (
                        "race",
                        Arrays.stream(Race.values())
                            .map(race->mvcConversionService.convert(race, String.class))
                            .toArray(String[]::new)
                    )
                    .contentType(MediaType.APPLICATION_JSON)
            )
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(),
            LadderMapStatsFilm.class
        );
        ladderMapStatsFilm.getFrames().sort(MapStatsFrame.NATURAL_ID_COMPARATOR);
        Assertions.assertThat(ladderMapStatsFilm)
            .usingRecursiveComparison()
            .isEqualTo(expectedResultFunction.apply(ladderMapStatsFilm));
    }

}
