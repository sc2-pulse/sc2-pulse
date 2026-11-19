// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.extension.AutoConfigureDatabase;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.QueueStats;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.LeagueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.PopulationStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.QueueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderLeagueStats;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.MergedLadderSearchStatsResult;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderStatsDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.model.validation.CursorNavigableResult;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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

/**
 * Region-local Blizzard season ids can differ from pulse season ids, e.g. CN
 * season 60 is global season 69. Pulse-internal data must use pulse season
 * ids, so new CN data must never be mixed with old global season 60 data.
 */
@SpringBootTest(classes = AllTestConfig.class)
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
@AutoConfigureDatabase(AutoConfigureDatabase.ExecutionPhase.CLASS)
public class OffsetRegionSeasonIT
{

    public static final int OLD_SEASON = 60;
    public static final int NEW_SEASON = 69;
    public static final int CN_NEW_SEASON = 60;
    public static final QueueType QUEUE_TYPE = QueueType.LOTV_1V1;
    public static final TeamType TEAM_TYPE = TeamType.ARRANGED;
    public static final BaseLeague.LeagueType LEAGUE_TYPE =
        BaseLeague.LeagueType.BRONZE;
    public static final BaseLeagueTier.LeagueTierType TIER_TYPE =
        BaseLeagueTier.LeagueTierType.FIRST;
    public static final OffsetDateTime OLD_START =
        SC2Pulse.offsetDateTime(2024, 1, 1);
    public static final OffsetDateTime NEW_START =
        SC2Pulse.offsetDateTime().minusDays(10).withNano(0);

    private static Season cnSeason;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LadderStatsDAO ladderStatsDAO;

    @Autowired
    private JdbcTemplate template;

    @Autowired @Qualifier("mvcConversionService")
    private ConversionService mvcConversionService;

    @BeforeAll
    public static void beforeAll
    (
        @Autowired SeasonGenerator seasonGenerator,
        @Autowired LeagueStatsDAO leagueStatsDAO,
        @Autowired QueueStatsDAO queueStatsDAO,
        @Autowired PopulationStateDAO populationStateDAO,
        @Autowired TeamDAO teamDAO,
        @Autowired SeasonStateDAO seasonStateDAO
    )
    {
        Season euOld = new Season(null, OLD_SEASON, Region.EU, 2024, 1,
            OLD_START, OLD_START.plusMonths(3));
        Season euNew = new Season(null, NEW_SEASON, Region.EU, 2026, 4,
            NEW_START, NEW_START.plusMonths(3));
        //CN season 60 has the same year and number as global season 69
        cnSeason = new Season(null, CN_NEW_SEASON, Region.CN, 2026, 4,
            NEW_START, NEW_START.plusMonths(3));
        generate(seasonGenerator, List.of(euOld, euNew));
        generate(seasonGenerator, List.of(cnSeason));

        leagueStatsDAO.mergeCalculateForSeason(NEW_SEASON);
        queueStatsDAO.mergeCalculateForSeason(NEW_SEASON);
        populationStateDAO.takeSnapshot(Set.of(NEW_SEASON));
        teamDAO.updateRanks(NEW_SEASON);
        seasonStateDAO.merge(NEW_START, NEW_SEASON);
    }

    private static void generate
    (
        SeasonGenerator seasonGenerator,
        List<Season> seasons
    )
    {
        seasonGenerator.generateSeason
        (
            seasons,
            List.of(LEAGUE_TYPE),
            List.of(QUEUE_TYPE),
            TEAM_TYPE,
            TIER_TYPE,
            1
        );
    }

    private <T> T getJson
    (
        MockHttpServletRequestBuilder request,
        TypeReference<T> type
    )
    throws Exception
    {
        return objectMapper.readValue
        (
            mvc.perform(request.contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            type
        );
    }

    private String convert(Object value)
    {
        return mvcConversionService.convert(value, String.class);
    }

    private List<LadderTeam> getLadder(int season, Region region)
    throws Exception
    {
        CursorNavigableResult<List<LadderTeam>> result = getJson
        (
            get("/api/teams")
                .queryParam("season", String.valueOf(season))
                .queryParam("region", convert(region))
                .queryParam("queue", convert(QUEUE_TYPE))
                .queryParam("teamType", convert(TEAM_TYPE)),
            new TypeReference<>(){}
        );
        return result.result();
    }

    private LadderTeam getCnTeam()
    throws Exception
    {
        List<LadderTeam> teams = getLadder(NEW_SEASON, Region.CN);
        assertEquals(1, teams.size());
        return teams.get(0);
    }

    private long getCnCharacterId()
    throws Exception
    {
        return getCnTeam().getMembers().get(0).getCharacter().getId();
    }

    @Test
    public void whenOffsetRegionSeason_thenStoreRegionAndPulseSeasonIds()
    {
        assertEquals(CN_NEW_SEASON, cnSeason.getBattlenetId());
        assertEquals(NEW_SEASON, cnSeason.getPulseSeasonId());
    }

    @Test
    public void whenFindSeasons_thenGroupByPulseSeasonId()
    throws Exception
    {
        List<Season> newSeasons = getJson
        (
            get("/api/seasons")
                .queryParam("season", String.valueOf(NEW_SEASON)),
            new TypeReference<>(){}
        );
        assertEquals(2, newSeasons.size());
        Season cn = newSeasons.stream()
            .filter(s->s.getRegion() == Region.CN)
            .findAny()
            .orElseThrow();
        assertEquals(CN_NEW_SEASON, cn.getBattlenetId());
        assertEquals(NEW_SEASON, cn.getPulseSeasonId());
        assertTrue(newSeasons.stream()
            .allMatch(s->s.getPulseSeasonId() == NEW_SEASON));

        List<Season> oldSeasons = getJson
        (
            get("/api/seasons")
                .queryParam("season", String.valueOf(OLD_SEASON)),
            new TypeReference<>(){}
        );
        assertEquals(1, oldSeasons.size());
        assertEquals(Region.EU, oldSeasons.get(0).getRegion());
        assertEquals(OLD_SEASON, oldSeasons.get(0).getPulseSeasonId());
    }

    @Test
    public void whenFindLadder_thenUsePulseSeasonId()
    throws Exception
    {
        LadderTeam cnTeam = getCnTeam();
        assertEquals(NEW_SEASON, cnTeam.getSeason());
        assertEquals(Region.CN, cnTeam.getRegion());
        //ranks and population are calculated across all regions
        assertEquals(2, cnTeam.getGlobalRank());
        assertEquals(1, cnTeam.getRegionRank());
        assertEquals(2, cnTeam.getPopulationState().getGlobalTeamCount());
        assertEquals(1, cnTeam.getPopulationState().getRegionTeamCount());

        assertTrue(getLadder(CN_NEW_SEASON, Region.CN).isEmpty());
        List<LadderTeam> euOldTeams = getLadder(OLD_SEASON, Region.EU);
        assertEquals(1, euOldTeams.size());
        assertEquals(OLD_SEASON, euOldTeams.get(0).getSeason());
    }

    @Test
    public void whenFindCharacterTeams_thenUsePulseSeasonId()
    throws Exception
    {
        long characterId = getCnCharacterId();
        List<LadderTeam> pulseIdTeams = getJson
        (
            get("/api/character-teams")
                .queryParam("characterId", String.valueOf(characterId))
                .queryParam("season", String.valueOf(NEW_SEASON)),
            new TypeReference<>(){}
        );
        assertEquals(1, pulseIdTeams.size());
        assertEquals(NEW_SEASON, pulseIdTeams.get(0).getSeason());

        List<LadderTeam> cnIdTeams = getJson
        (
            get("/api/character-teams")
                .queryParam("characterId", String.valueOf(characterId))
                .queryParam("season", String.valueOf(CN_NEW_SEASON)),
            new TypeReference<>(){}
        );
        assertTrue(cnIdTeams.isEmpty());
    }

    @Test
    public void whenCalculateQueueStats_thenIncludeOffsetRegion()
    throws Exception
    {
        List<QueueStats> stats = getJson
        (
            get
            (
                "/api/ladder/stats/queue/{queue}/{teamType}",
                convert(QUEUE_TYPE),
                convert(TEAM_TYPE)
            ),
            new TypeReference<>(){}
        );
        assertEquals(1, stats.size());
        QueueStats newStats = stats.get(0);
        assertEquals(NEW_SEASON, newStats.getSeason());
        assertEquals(2, stats.get(0).getPlayerCount());
    }

    /*
        Verified on the DAO level because the response DTOs aren't
        deserializable. LeagueStats of LadderLeagueStats has no Jackson
        creator. The only MergedLadderSearchStatsResult constructor takes raw
        stats, which aren't serialized.
     */
    @Test
    public void whenCalculateLeagueStats_thenUsePulseSeasonId()
    {
        List<LadderLeagueStats> leagueStats = ladderStatsDAO.findLeagueStats
        (
            NEW_SEASON,
            List.of(Region.CN),
            List.of(LEAGUE_TYPE),
            QUEUE_TYPE,
            TEAM_TYPE
        );
        assertEquals(1, leagueStats.size());
        assertEquals(1, leagueStats.get(0).getLeagueStats().getTeamCount());
        Season statsSeason = leagueStats.get(0).getSeason();
        assertEquals(CN_NEW_SEASON, statsSeason.getBattlenetId());
        assertEquals(NEW_SEASON, statsSeason.getPulseSeasonId());

        Map<Integer, MergedLadderSearchStatsResult> stats = ladderStatsDAO
            .findStats
            (
                Set.of(Region.CN),
                Set.of(LEAGUE_TYPE),
                QUEUE_TYPE,
                TEAM_TYPE
            );
        assertEquals(Set.of(NEW_SEASON), stats.keySet());
        assertEquals
        (
            1L,
            stats.get(NEW_SEASON).getRegionTeamCount().get(Region.CN)
        );
    }

    @Test
    public void whenMergeSeasonState_thenIncludeOffsetRegion()
    {
        List<Integer> seasonIds = template.queryForList
        (
            "SELECT season_id FROM season_state",
            Integer.class
        );
        assertTrue(seasonIds.contains(cnSeason.getId()));
    }

    @Test
    public void whenGetCharacterSummary_thenUsePulseSeasonId()
    throws Exception
    {
        long characterId = getCnCharacterId();
        Map<String, Object> summary = template.queryForMap
        (
            """
                SELECT *
                FROM get_player_character_summary
                (
                    ARRAY[?]::bigint[],
                    '2000-01-01'::timestamp with time zone,
                    ARRAY[1, 2, 3, 4]::smallint[]
                )
            """,
            characterId
        );
        assertEquals(2, ((Number) summary.get("global_rank_last")).intValue());
    }

}
