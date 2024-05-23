// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static com.nephest.battlenet.sc2.model.Race.PROTOSS;
import static com.nephest.battlenet.sc2.model.Race.TERRAN;
import static com.nephest.battlenet.sc2.model.Race.ZERG;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.MapStatsFilmSpec;
import com.nephest.battlenet.sc2.model.local.MatchUp;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.LeagueStatsDAO;
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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.sql.DataSource;
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
public class MapStatsFilmRaceFilterIT
{

    @Autowired @Qualifier("mvcConversionService")
    private ConversionService mvcConversionService;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

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
        @Autowired MapStatsFilmTestService mapStatsFilmTestService
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        mapService.getMapStatsInstant().setValue(SC2Pulse.instant());
        mapService.setDbInitialized(false);
        mapStatsFilmTestService.generateFilms((startFrom)->{
            //tvp
            seasonGenerator.createMatch(29L, 30L, startFrom,
                MapStatsFilmTestService.FRAME_OFFSET);
            //tvz
            seasonGenerator.createMatch(33L, 31L, startFrom.plusSeconds(1),
                MapStatsFilmTestService.FRAME_OFFSET);
            return List.of(29L, 30L, 31L, 33L);
        });
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

    public static Stream<Arguments> testRaceFilter()
    {
        return Stream.of
        (
            Arguments.of(List.of(TERRAN, PROTOSS), List.of(new MatchUp(PROTOSS, TERRAN))),
            Arguments.of
            (
                List.of(TERRAN, PROTOSS, ZERG),
                List.of(new MatchUp(TERRAN, ZERG), new MatchUp(PROTOSS, TERRAN))
            )
        );
    }

    @MethodSource
    @ParameterizedTest
    public void testRaceFilter(List<Race> races, List<MatchUp> expectedMatchUps)
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
                        mvcConversionService.convert(QueueType.LOTV_1V1, String.class)
                    )
                    .queryParam
                    (
                        "teamType",
                        mvcConversionService.convert(TeamType.ARRANGED, String.class)
                    )
                    .queryParam
                    (
                        "league",
                        mvcConversionService.convert(BaseLeague.LeagueType.BRONZE, String.class)
                    )
                    .queryParam
                    (
                        "tier",
                        mvcConversionService.convert(BaseLeagueTier.LeagueTierType.FIRST, String.class)
                    )
                    .queryParam
                    (
                        "race",
                        races.stream()
                            .map(race->mvcConversionService.convert(race, String.class))
                            .toArray(String[]::new)
                    )
                    .contentType(MediaType.APPLICATION_JSON)
            )
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(),
            LadderMapStatsFilm.class
        );
        assertIterableEquals
        (
            expectedMatchUps,
            ladderMapStatsFilm.getFilms().values().stream()
                .map(film->ladderMapStatsFilm.getSpecs().get(film.getMapStatsFilmSpecId()))
                .sorted(Comparator.comparing(MapStatsFilmSpec::getRace))
                .map(spec->new MatchUp(spec.getRace(), spec.getVersusRace()))
                .toList()
        );
    }

}
