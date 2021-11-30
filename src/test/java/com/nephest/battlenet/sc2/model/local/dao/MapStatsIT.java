// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.local.*;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMapStats;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderMapStatsDAO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AllTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class MapStatsIT
{

    @Autowired
    private MatchDAO matchDAO;

    @Autowired
    private MatchParticipantDAO matchParticipantDAO;

    @Autowired
    private SC2MapDAO mapDAO;

    @Autowired
    private LadderMapStatsDAO ladderMapStatsDAO;

    @Autowired
    private MapStatsDAO mapStatsDAO;

    @Autowired
    private TeamDAO teamDAO;

    @Autowired
    private TeamStateDAO teamStateDAO;

    @Autowired
    private SeasonDAO seasonDAO;

    @Autowired
    private LeagueDAO leagueDAO;

    @Autowired
    private LeagueStatsDAO leagueStatsDAO;

    @Autowired
    private SeasonGenerator seasonGenerator;

    @Autowired @Qualifier("mvcConversionService")
    private ConversionService conversionService;

    @Autowired
    private ObjectMapper objectMapper;

    private static MockMvc mvc;

    @BeforeEach
    public void beforeAll(@Autowired DataSource dataSource, @Autowired WebApplicationContext webApplicationContext)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
            mvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .alwaysDo(print())
                .build();
        }
    }

    @AfterEach
    public void afterAll(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }

    @Test
    public void testStats()
    throws Exception
    {
        List<Region> regions = List.of(Region.US, Region.EU);
        List<BaseLeague.LeagueType> leagueTypes = List.of(BaseLeague.LeagueType.values());
        int teamsPerLeague = 100;
        seasonGenerator.generateDefaultSeason
        (
            regions,
            leagueTypes,
            List.of(QueueType.LOTV_1V1),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            teamsPerLeague,
            true
        );

        List<Season> seasons = seasonDAO.findListByIds(List.of(1, 2));
        seasons.sort(Comparator.comparing(Season::getId));
        List<League> leagues = leagueDAO.find(IntStream.rangeClosed(1, 14).boxed().collect(Collectors.toList()));
        leagues.sort(Comparator.comparing(League::getId));
        List<SC2Map> maps = new ArrayList<>();
        for(int i = 0; i < 7; i++) maps.add(mapDAO.merge(new SC2Map(null, "map" + i))[0]);
        List<SC2Map> statsMaps =  List.of(maps.get(1), maps.get(2));
        OffsetDateTime start = OffsetDateTime.now().minusHours(10);
        generateMatches(regions, start, leagueTypes.size(), teamsPerLeague, 2);
        generateMatches(regions, start.plusHours(1), leagueTypes.size(), teamsPerLeague, 2);
        generateMatches(regions, start.plusHours(2), leagueTypes.size(), teamsPerLeague, 3);
        mapStatsDAO.add(start, start.plusYears(1));

        //sum stats
        LadderMapStats allStats1 = getStats
        (
            SeasonGenerator.DEFAULT_SEASON_ID,
            List.of(Region.values()),
            List.of(BaseLeague.LeagueType.values()),
            null
        );
        assertNull(allStats1.getMap());
        //(players / 2)(matches) * 3(maps) / 2(regions)
        verifyStats(allStats1, seasons, leagues, regions, statsMaps,
            //only 2/3 of matches has duration
            (g)->(g/3) * 2 * 3600 - (g/3) * 2 * MatchDAO.DURATION_OFFSET,
            (g)->(g/3) * 2,
            150, 150, 150, 150,
            //a part of platinum range is inactive due to gm cutoff, so the match-up numbers are shifted
            111, 111, 114, 114,
            120, 123, 120, 120,
            123, 120, 120, 120,
            21, 21, 21, 21);

        //map3 stats
        LadderMapStats map3Stats1 = getStats
        (
            SeasonGenerator.DEFAULT_SEASON_ID,
            List.of(Region.values()),
            List.of(BaseLeague.LeagueType.values()),
            3
        );
        assertEquals(maps.get(2), map3Stats1.getMap());
        //(players / 2)(matches) / 2(regions)
        verifyStats(map3Stats1, seasons, leagues, regions, statsMaps,
            (g)->g * 3600 - g * MatchDAO.DURATION_OFFSET,
            (g)->g,
            50, 50, 50, 50,
            //a part of platinum range is inactive due to gm cutoff, so the match-up numbers are shifted
            37, 37, 38, 38,
            40, 41, 40, 40,
            41, 40, 40, 40,
            7, 7, 7, 7);

        //double the stats
        mapStatsDAO.add(start, start.plusYears(1));

        //sum stats
        LadderMapStats allStats2 = getStats
        (
            SeasonGenerator.DEFAULT_SEASON_ID,
            List.of(Region.values()),
            List.of(BaseLeague.LeagueType.values()),
            null
        );
        assertNull(allStats2.getMap());
        //(players / 2)(matches) * 3(maps) / 2(regions)
        verifyStats(allStats2, seasons, leagues, regions, statsMaps,
            //only 2/3 of matches has duration
            (g)->(g/3) * 2 * 3600 - (g/3) * 2 * MatchDAO.DURATION_OFFSET,
            (g)->(g/3) * 2,
            300, 300, 300, 300,
            //a part of platinum range is inactive due to gm cutoff, so the match-up numbers are shifted
            222, 222, 228, 228,
            240, 246, 240, 240,
            246, 240, 240, 240,
            42, 42, 42, 42);

        //map3 stats
        LadderMapStats map3Stats2 = getStats
        (
            SeasonGenerator.DEFAULT_SEASON_ID,
            List.of(Region.values()),
            List.of(BaseLeague.LeagueType.values()),
            3
        );
        assertEquals(maps.get(2), map3Stats2.getMap());
        //(players / 2)(matches) / 2(regions)
        verifyStats(map3Stats2, seasons, leagues, regions, statsMaps,
            (g)->g * 3600 - g * MatchDAO.DURATION_OFFSET,
            (g)->g,
            100, 100, 100, 100,
            //a part of platinum range is inactive due to gm cutoff, so the match-up numbers are shifted
            74, 74, 76, 76,
            80, 82, 80, 80,
            82, 80, 80, 80,
            14, 14, 14, 14);
    }

    private void generateMatches
    (List<Region> regions, OffsetDateTime odt, int leagueCount, int teamsPerLeague, int mapId)
    {
        List<Tuple2<Match, MatchParticipant[]>> matches = new ArrayList<>();
        List<TeamState> states = new ArrayList<>();
        for (int li = 0; li < leagueCount; li++)
        {
            int leagueOffset = li * regions.size() * teamsPerLeague;
            for (int ri = 0; ri < regions.size(); ri++)
            {
                Region region = regions.get(ri);
                long regionOffset = (long) ri * teamsPerLeague;
                //1 match per 2 players
                for (int ti = 0; ti < teamsPerLeague / 2; ti++)
                {
                    int teamOsset = ti * 2;
                    //+1 because ids are 1 based
                    int divisionId = li * regions.size() + ri + 1;
                    long char1Id = leagueOffset + regionOffset + teamOsset + 1;
                    long char2Id = char1Id + 1;
                    //unique date to prevent duplicates
                    OffsetDateTime uniqueOdt = odt.plusSeconds(char1Id);
                    states.add(new TeamState(char1Id, uniqueOdt, divisionId, 1, (int) char1Id));
                    states.add(new TeamState(char2Id, uniqueOdt, divisionId, 1, (int) char2Id));
                    Match match = new Match(null, uniqueOdt, BaseMatch.MatchType._1V1, mapId, region);
                    MatchParticipant[] participants = new MatchParticipant[]
                    {
                        new MatchParticipant(null, char1Id, BaseMatch.Decision.WIN),
                        new MatchParticipant(null, char2Id, BaseMatch.Decision.LOSS)
                    };
                    matches.add(Tuples.of(match, participants));
                }
            }
        }
        teamStateDAO.saveState(states.toArray(TeamState[]::new));
        matchDAO.merge(matches.stream().map(Tuple2::getT1).toArray(Match[]::new));
        matches.forEach(m->{
            m.getT2()[0].setMatchId(m.getT1().getId());
            m.getT2()[1].setMatchId(m.getT1().getId());
        });
        matchParticipantDAO.merge(matches.stream().flatMap(m->Arrays.stream(m.getT2())).toArray(MatchParticipant[]::new));
        matchDAO.updateDuration(odt);
        leagueStatsDAO.mergeCalculateForSeason(SeasonGenerator.DEFAULT_SEASON_ID);
        teamDAO.updateRanks(SeasonGenerator.DEFAULT_SEASON_ID);
        teamStateDAO.updateRanks(odt, Set.of(SeasonGenerator.DEFAULT_SEASON_ID));
        matchParticipantDAO.identify(SeasonGenerator.DEFAULT_SEASON_ID, odt);
    }

    private LadderMapStats getStats
    (int season, Collection<Region> regions, Collection<BaseLeague.LeagueType> leagues, Integer mapId)
    throws Exception
    {
        String reqTemplate = "/api/ladder/stats/map/%1$s/%2$s/%3$s/%4$s/%5$s%6$s";
        String regionStrings = regions.stream()
            .map(r->conversionService.convert(r, String.class))
            .collect(Collectors.joining(","));
        String leagueStrings = leagues.stream()
            .map(l->conversionService.convert(l, String.class))
            .collect(Collectors.joining(","));
        String queueType = conversionService.convert(QueueType.LOTV_1V1, String.class);
        String teamType = conversionService.convert(TeamType.ARRANGED, String.class);
        String req = String.format(reqTemplate,
            season, queueType, teamType, regionStrings, leagueStrings, mapId == null ? "" : "/" + mapId);
        return objectMapper.readValue(mvc.perform
        (
            get(req)
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString(), LadderMapStats.class);
    }

    private void verifyStats
    (
        LadderMapStats stats,
        List<Season> seasons,
        List<League> leagues,
        List<Region> regions,
        List<SC2Map> maps,
        Function<Integer, Integer> durationCalc,
        Function<Integer, Integer> gamesWithDurationCalc,
        Integer... games
    )
    {
        assertIterableEquals(maps, stats.getMaps());
        assertIterableEquals(seasons, stats.getSeasons().values().stream()
            .sorted(Comparator.comparing(Season::getId)).collect(Collectors.toList()));
        assertIterableEquals(leagues, stats.getLeagues().values().stream()
            .sorted(Comparator.comparing(League::getId)).collect(Collectors.toList()));
        assertEquals(40, stats.getStats().size());

        for(Region region : regions)
        {
            Season season = stats.getSeasons().values().stream()
                .filter(s->s.getRegion() == region).findAny().orElseThrow();
            //using top% based leagues, ignoring real leagues

            //gm is top 200
            verifyLeagueStats(stats, season, BaseLeague.LeagueType.GRANDMASTER, durationCalc, gamesWithDurationCalc,
                games[0], games[1], games[2], games[3]);

            //masters and diamond are absent because top 200 are gm, and the next team is top 28%, which makes it a
            // platinum team
            assertTrue(getStats(stats, season, BaseLeague.LeagueType.MASTER).isEmpty());
            assertTrue(getStats(stats, season, BaseLeague.LeagueType.DIAMOND).isEmpty());

            //a part of platinum range is inactive due to gm cutoff, so the match-up numbers are shifted
            verifyLeagueStats(stats, season, BaseLeague.LeagueType.PLATINUM, durationCalc, gamesWithDurationCalc,
                games[4], games[5], games[6], games[7]);
            verifyLeagueStats(stats, season, BaseLeague.LeagueType.GOLD, durationCalc, gamesWithDurationCalc,
                games[8], games[9], games[10], games[11]);
            verifyLeagueStats(stats, season, BaseLeague.LeagueType.SILVER, durationCalc, gamesWithDurationCalc,
                games[12], games[13], games[14], games[15]);
            verifyLeagueStats(stats, season, BaseLeague.LeagueType.BRONZE, durationCalc, gamesWithDurationCalc,
                games[16], games[17], games[18], games[19]);
        }
    }

    private void verifyLeagueStats
    (
        LadderMapStats stats,
        Season season,
        BaseLeague.LeagueType leagueType,
        Function<Integer, Integer> durationCalc,
        Function<Integer, Integer> gamesWithDurationCalc,
        int games1, int games2, int games3, int games4
    )
    {
        List<MapStats> leagueStats = getStats(stats, season, leagueType);
        assertEquals(4, leagueStats.size());
        checkMatchUpPair(leagueStats.get(0), leagueStats.get(1), Race.TERRAN, Race.PROTOSS, durationCalc,
            gamesWithDurationCalc, games1, games2);
        checkMatchUpPair(leagueStats.get(2), leagueStats.get(3), Race.ZERG, Race.RANDOM, durationCalc,
            gamesWithDurationCalc, games3, games4);
    }

    private List<MapStats> getStats(LadderMapStats stats, Season season, BaseLeague.LeagueType leagueType)
    {
        League league = stats.getLeagues().values().stream()
            .filter(l->l.getType() == leagueType && l.getSeasonId().equals(season.getId()))
            .findAny().orElseThrow();
        return stats.getStats()
            .stream()
            .filter(s -> s.getLeagueId().equals(league.getId()))
            .sorted(Comparator.comparing(MapStats::getRace).thenComparing(MapStats::getVersusRace))
            .collect(Collectors.toList());
    }

    private void checkMatchUpPair
    (
        MapStats stats1, MapStats stats2,
        Race race1, Race race2,
        Function<Integer, Integer> durationCalc, Function<Integer, Integer> gamesWithDurationCalc,
        int games1, int games2
    )
    {
        assertEquals(race1, stats1.getRace());
        assertEquals(race2, stats1.getVersusRace());
        assertEquals(games1, stats1.getGamesTotal());
        assertEquals(gamesWithDurationCalc.apply(games1), stats1.getGamesWithDuration());
        assertEquals(games1, stats1.getWins());
        assertEquals(0, stats1.getLosses());
        assertEquals(0, stats1.getTies());
        assertEquals(durationCalc.apply(games1), stats1.getDuration());

        assertEquals(race2, stats2.getRace());
        assertEquals(race1, stats2.getVersusRace());
        assertEquals(games2, stats2.getGamesTotal());
        assertEquals(gamesWithDurationCalc.apply(games2), stats2.getGamesWithDuration());
        assertEquals(0, stats2.getWins());
        assertEquals(games2, stats2.getLosses());
        assertEquals(0, stats2.getTies());
        assertEquals(durationCalc.apply(games2), stats2.getDuration());
    }

}
