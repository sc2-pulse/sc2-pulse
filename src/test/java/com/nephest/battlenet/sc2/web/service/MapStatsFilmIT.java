// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static com.nephest.battlenet.sc2.model.Race.PROTOSS;
import static com.nephest.battlenet.sc2.model.Race.ZERG;
import static com.nephest.battlenet.sc2.web.service.MapService.FILM_FRAME_DURATION;
import static com.nephest.battlenet.sc2.web.service.MapStatsFilmTestService.FRAME_NUMBER;
import static com.nephest.battlenet.sc2.web.service.MapStatsFilmTestService.FRAME_OFFSET;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.MapStatsFilm;
import com.nephest.battlenet.sc2.model.local.MapStatsFilmSpec;
import com.nephest.battlenet.sc2.model.local.MapStatsFrame;
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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import reactor.core.Disposable;

@SpringBootTest(classes = AllTestConfig.class)
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class MapStatsFilmIT
{

    @Autowired
    private TeamDAO teamDAO;

    @Autowired
    private TeamStateDAO teamStateDAO;

    @Autowired
    private MatchParticipantDAO matchParticipantDAO;

    @Autowired
    private MatchDAO matchDAO;

    @Autowired
    private PopulationStateDAO populationStateDAO;

    @Autowired
    private LeagueStatsDAO leagueStatsDAO;

    @Autowired
    private MapStatsFilmSpecDAO mapStatsFilmSpecDAO;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired
    private EventService eventService;

    @Autowired
    private MapService mapService;
    
    @Autowired
    private MapStatsFilmTestService mapStatsFilmTestService;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired @Qualifier("mvcConversionService")
    private ConversionService mvcConversionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void beforeEach(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        mapService.getMapStatsInstant().setValue(SC2Pulse.instant());
        mapService.setDbInitialized(false);
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

    private ResultActions getStandardFilm()
    throws Exception
    {
        return mvc.perform
        (
            get("/api/stats/balance-reports")
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
                .contentType(MediaType.APPLICATION_JSON)
        );
    }

    @Test
    public void testMapFilmStats()
    throws Exception
    {

        OffsetDateTime[] odts = new OffsetDateTime[1];
        List<Long> teamIds = List.of(38L, 39L);
        BlockingQueue<UpdateContext> updateContexts
            = mapStatsFilmTestService.generateFilms(startFrom->{
            odts[0] = startFrom;
            seasonGenerator.createMatches
            (
                BaseMatch.MatchType._1V1,
                38L, 39L,
                new long[]{38L}, new long[]{39L},
                startFrom,
                Region.EU,
                1, 1, 1, 1, 1
            );
            seasonGenerator.createMatches
            (
                BaseMatch.MatchType._1V1,
                //switch ids to change a winner
                39L, 38L,
                new long[]{39L}, new long[]{38L},
                startFrom.plusSeconds(MatchDAO.DURATION_OFFSET + FRAME_OFFSET),
                Region.EU,
                1, 1, 1, 1, 1
            );
            seasonGenerator.createMatches
            (
                BaseMatch.MatchType._1V1,
                38L, 39L,
                new long[]{38L}, new long[]{39L},
                startFrom.plusSeconds(MatchDAO.DURATION_OFFSET * 2 + FRAME_OFFSET * 2),
                Region.EU,
                1, 1, 1, 1, 1
            );
            return teamIds;
        });
        OffsetDateTime startFrom = odts[0];

        List<MapStatsFilmSpec> specs = mapStatsFilmSpecDAO
            .find(MapService.MATCH_UPS, FILM_FRAME_DURATION);
        List<MapStatsFilmSpec> expectedSpecs = MapService.MATCH_UPS.stream()
            .map(mu->new MapStatsFilmSpec(
                null, mu.getRaces().get(0), mu.getVersusRaces().get(0), FILM_FRAME_DURATION))
            .collect(Collectors.toList());
        assertTrue(specs.size() == expectedSpecs.size() && specs.containsAll(expectedSpecs));
        Map<Integer, MapStatsFilmSpec> specsMap = specs.stream()
            .collect(Collectors.toMap(MapStatsFilmSpec::getId, Function.identity()));
        MapStatsFilmSpec matchUpSpec = specs.stream()
            .filter(spec->spec.getRace() == ZERG && spec.getVersusRace() == PROTOSS)
            .findAny()
            .orElseThrow();

        LadderMapStatsFilm ladderMapStatsFilm1 = objectMapper.readValue
        (
            getStandardFilm()
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(),
            LadderMapStatsFilm.class
        );
        ladderMapStatsFilm1.getFrames().sort(Comparator.comparing(
            MapStatsFrame::getNumber, Comparator.nullsLast(Comparator.naturalOrder())));
        Assertions.assertThat(ladderMapStatsFilm1)
            .usingRecursiveComparison()
            .isEqualTo(new LadderMapStatsFilm(
                Map.of(1, SeasonGenerator.defaultMap()),
                Map.of(1, SeasonGenerator.defaultSeason()),
                Map.of(1, SeasonGenerator.defaultLeague()),
                Map.of(1, SeasonGenerator.defaultTier()),
                specsMap,
                Map.of(1, new MapStatsFilm(
                    1, 1, 1, matchUpSpec.getId(), false
                )),
                List.of
                (
                    new MapStatsFrame(1, FRAME_NUMBER, 1, 2),
                    new MapStatsFrame(1, null, 0, 1)
                )
            ));

        //add new stats
        OffsetDateTime startFrom2 = startFrom.plusMonths(1);
        Instant mucInstant2 = startFrom2.plusDays(1).toInstant();
        seasonGenerator.createMatches
        (
            BaseMatch.MatchType._1V1,
            38L, 39L,
            new long[]{38L}, new long[]{39L},
            startFrom2,
            Region.EU,
            1, 1, 1, 1, 1
        );
        seasonGenerator.createMatches
        (
            BaseMatch.MatchType._1V1,
            39L, 38L,
            new long[]{39L}, new long[]{38L},
            startFrom2.plusSeconds(MatchDAO.DURATION_OFFSET + FRAME_OFFSET),
            Region.EU,
            1, 1, 1, 1, 1
        );
        jdbcTemplate.update("DELETE FROM team_state WHERE timestamp >= ? ", startFrom2);
        teamStateDAO.takeSnapshot(teamIds, startFrom2);
        seasonGenerator.takeTeamSnapshot(teamIds, startFrom2, FRAME_OFFSET, 1);
        matchParticipantDAO.identify(SeasonGenerator.DEFAULT_SEASON_ID, startFrom2);
        matchDAO.updateDuration(startFrom2);
        eventService.createMatchUpdateEvent(new MatchUpdateContext(
            Map.of(), new UpdateContext(mucInstant2, mucInstant2)));
        updateContexts.poll(5, TimeUnit.SECONDS);
        updateContexts.clear();
        LadderMapStatsFilm ladderMapStatsFilm2 = objectMapper.readValue
        (
            getStandardFilm()
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(),
            LadderMapStatsFilm.class
        );
        ladderMapStatsFilm2.getFrames().sort(Comparator.comparing(
            MapStatsFrame::getNumber, Comparator.nullsLast(Comparator.naturalOrder())));
        Assertions.assertThat(ladderMapStatsFilm2)
            .usingRecursiveComparison()
            .isEqualTo(new LadderMapStatsFilm(
                Map.of(1, SeasonGenerator.defaultMap()),
                Map.of(1, SeasonGenerator.defaultSeason()),
                Map.of(1, SeasonGenerator.defaultLeague()),
                Map.of(1, SeasonGenerator.defaultTier()),
                specsMap,
                Map.of(1, new MapStatsFilm(
                    1, 1, 1, matchUpSpec.getId(), false
                )),
                List.of
                (
                    new MapStatsFrame(1, FRAME_NUMBER, 2, 3),
                    new MapStatsFrame(1, null, 0, 2)
                )
            ));
    }

    @Test
    public void whenNoClearWinnerAndLoser_thenSkipMatch()
    throws Exception
    {
        BlockingQueue<UpdateContext> updateContexts = new ArrayBlockingQueue<>(1);
        Disposable sub = mapService.getUpdateEvent().subscribe(updateContexts::add);
        try
        {
            long frameOffset = FILM_FRAME_DURATION.toSeconds() * 5;
            seasonGenerator.generateDefaultSeason(10, true);
            leagueStatsDAO.calculateForSeason(SeasonGenerator.DEFAULT_SEASON_ID);
            populationStateDAO.takeSnapshot(Set.of(SeasonGenerator.DEFAULT_SEASON_ID));
            teamDAO.updateRanks(SeasonGenerator.DEFAULT_SEASON_ID);
            OffsetDateTime startFrom = SC2Pulse.offsetDateTime().plusMonths(1);
            Instant mucInstant = startFrom.plusDays(1).toInstant();
            seasonGenerator.createMatches
            (
                BaseMatch.MatchType._1V1,
                9L, 10L,
                new long[]{8L, 9L}, new long[]{10L},
                startFrom,
                Region.EU,
                1, 1, 1, 1, 1
            );
            seasonGenerator.createMatches
            (
                BaseMatch.MatchType._1V1,
                9L, 10L,
                new long[]{8L, 9L}, new long[]{10L},
                startFrom.plusSeconds(MatchDAO.DURATION_OFFSET + frameOffset),
                Region.EU,
                1, 1, 1, 1, 1
            );
            List<Long> teamIds = List.of(9L, 10L);
            jdbcTemplate.update("DELETE FROM team_state WHERE timestamp >= ? ", startFrom);
            teamStateDAO.takeSnapshot(teamIds, startFrom);
            seasonGenerator.takeTeamSnapshot(teamIds, startFrom, frameOffset, 1);
            matchParticipantDAO.identify(SeasonGenerator.DEFAULT_SEASON_ID, startFrom);
            matchDAO.updateDuration(startFrom);
            eventService.createMatchUpdateEvent(
                new MatchUpdateContext(Map.of(), new UpdateContext(mucInstant, mucInstant)));
            updateContexts.poll(5, TimeUnit.SECONDS);

            LadderMapStatsFilm ladderMapStatsFilm = objectMapper.readValue
            (
                getStandardFilm()
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(),
                LadderMapStatsFilm.class
            );
            assertTrue(ladderMapStatsFilm.getFilms().isEmpty());
        }
        finally
        {
            sub.dispose();
        }
    }

}
