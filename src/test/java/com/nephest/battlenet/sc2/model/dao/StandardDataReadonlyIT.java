// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardPlayerCharacter;
import com.nephest.battlenet.sc2.model.blizzard.dao.BlizzardDAO;
import com.nephest.battlenet.sc2.model.local.Division;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.PopulationState;
import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.DAOUtils;
import com.nephest.battlenet.sc2.model.local.dao.DivisionDAO;
import com.nephest.battlenet.sc2.model.local.dao.LeagueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.PopulationStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderTeamStateDAO;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import reactor.util.function.Tuple3;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class StandardDataReadonlyIT
{

    public static final int TEAMS_PER_LEAGUE = 5;

    @Autowired
    private DivisionDAO divisionDAO;

    @Autowired
    private TeamDAO teamDAO;

    @Autowired
    private LadderTeamStateDAO ladderTeamStateDAO;

    @Autowired
    private BlizzardDAO blizzardDAO;

    @Autowired
    private PlayerCharacterDAO playerCharacterDAO;

    @Autowired
    private PopulationStateDAO populationStateDAO;

    @Autowired
    private JdbcTemplate template;

    @Autowired @Qualifier("sc2StatsConversionService")
    private ConversionService conversionService;

    @BeforeAll
    public static void init
    (
        @Autowired DataSource dataSource,
        @Autowired SeasonGenerator generator,
        @Autowired LeagueStatsDAO leagueStatsDAO,
        @Autowired PopulationStateDAO populationStateDAO
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }

        generator.generateDefaultSeason
        (
            List.of(Region.values()),
            List.of(BaseLeague.LeagueType.values()),
            List.of(QueueType.values()),
            TeamType.ARRANGED,
            BaseLeagueTier.LeagueTierType.FIRST,
            TEAMS_PER_LEAGUE
        );
        leagueStatsDAO.calculateForSeason(SeasonGenerator.DEFAULT_SEASON_ID);
        populationStateDAO.takeSnapshot(List.of(SeasonGenerator.DEFAULT_SEASON_ID));
    }

    @Test
    public void testDivisionFinders()
    {
        Map<Division, PlayerCharacter> ids = divisionDAO.findProfileDivisionIds
        (
            SeasonGenerator.DEFAULT_SEASON_ID,
            Region.US,
            new BaseLeague.LeagueType[]{BaseLeague.LeagueType.DIAMOND, BaseLeague.LeagueType.MASTER},
            QueueType.LOTV_1V1,
            TeamType.ARRANGED
        );
        assertEquals(2, ids.size());
        List<Map.Entry<Division, PlayerCharacter>> orderedIds= ids.entrySet().stream()
            .sorted((e, e2)->e.getKey().getId().compareTo(e2.getKey().getId()))
            .collect(Collectors.toList());
        //diamond
        assertEquals(divisionDAO.findDivision(SeasonGenerator.DEFAULT_SEASON_ID,
            Region.US, QueueType.LOTV_1V1, TeamType.ARRANGED, 24004).get(),
            orderedIds.get(0).getKey());
        //master
        assertEquals(divisionDAO.findDivision(SeasonGenerator.DEFAULT_SEASON_ID,
            Region.US, QueueType.LOTV_1V1, TeamType.ARRANGED, 24405).get(),
            orderedIds.get(1).getKey());
        //grandmaster
        assertEquals(24806, divisionDAO.findLastDivision(SeasonGenerator.DEFAULT_SEASON_ID,
            Region.US, QueueType.LOTV_1V1, TeamType.ARRANGED).get());
        assertEquals(36116, divisionDAO.findLastDivision(SeasonGenerator.DEFAULT_SEASON_ID, Region.EU).get());

        List<Long> longIds = divisionDAO.findDivisionIds
        (
            SeasonGenerator.DEFAULT_SEASON_ID,
            Region.US,
            new BaseLeague.LeagueType[]{BaseLeague.LeagueType.DIAMOND, BaseLeague.LeagueType.MASTER},
            new QueueType[]{QueueType.LOTV_1V1},
            TeamType.ARRANGED
        );
        assertEquals(2, ids.size());
        longIds.sort(Comparator.naturalOrder());
        assertEquals(orderedIds.get(0).getKey().getBattlenetId(), longIds.get(0));
        assertEquals(orderedIds.get(1).getKey().getBattlenetId(), longIds.get(1));

        int count = divisionDAO.getDivisionCount
        (
            SeasonGenerator.DEFAULT_SEASON_ID,
            Region.US,
            new BaseLeague.LeagueType[]{BaseLeague.LeagueType.DIAMOND, BaseLeague.LeagueType.MASTER},
            QueueType.LOTV_1V1,
            TeamType.ARRANGED
        );
        assertEquals(2, count);
    }

    @Test
    public void testTeamLegacyIdGeneration()
    {
        BlizzardPlayerCharacter c1 = new BlizzardPlayerCharacter(1L, 1, "name1");
        BlizzardPlayerCharacter c2 = new BlizzardPlayerCharacter(2L, 1, "name2");
        assertEquals(
            new BigInteger("123"),
            teamDAO.legacyIdOf(new BlizzardPlayerCharacter[]{c2}, Race.ZERG)
        );
        assertEquals(
            new BigInteger("1112"),
            teamDAO.legacyIdOf(new BlizzardPlayerCharacter[]{c1, c2})
        );
        assertEquals(
            new BigInteger("1112"),
            teamDAO.legacyIdOf(new BlizzardPlayerCharacter[]{c2, c1})
        );
        assertEquals(
            new BigInteger("111213"),
            teamDAO.legacyIdOf(new BlizzardPlayerCharacter[]{c1, c2}, Race.ZERG, Race.TERRAN)
        );
    }

    @Test
    public void testLadderLegacyIdFinder()
    {
        int charCount = 5;
        List<Tuple3<Region, BlizzardPlayerCharacter[], Long>> ids = blizzardDAO.findLegacyLadderIds
        (
            SeasonGenerator.DEFAULT_SEASON_ID,
            new Region[]{Region.US},
            new QueueType[]{QueueType.LOTV_2V2},
            BaseLeague.LeagueType.values(),
            charCount
        );
        assertEquals(BaseLeague.LeagueType.values().length, ids.size());

        int queuePosition = 9; //4 wol, 4 hots, 1 lotv(1v1)
        int teamPosition = TEAMS_PER_LEAGUE * BaseLeague.LeagueType.values().length * Region.values().length * queuePosition;
        int divisionPosition = teamPosition / TEAMS_PER_LEAGUE;
        for(int i = 0; i < ids.size(); i++)
        {
            Tuple3<Region, BlizzardPlayerCharacter[], Long> id = ids.get(i);
            assertEquals(Region.US, id.getT1());
            //replicating SeasonGenerator's division id
            assertEquals(Long.valueOf((divisionPosition + (long) i * Region.values().length) + "0" + i), id.getT3());
            assertEquals(charCount, id.getT2().length);
            for(int charIx = 0; charIx < id.getT2().length; charIx++)
                //0 at the end means it's a first team member only
                assertEquals(Long.valueOf(teamPosition + (i * Region.values().length * TEAMS_PER_LEAGUE) + charIx + "0"), id.getT2()[charIx].getId());
        }

    }

    @Test
    public void whenNoLegacyIds_thenReturnEmptyCollection()
    {
        List<Tuple3<Region, BlizzardPlayerCharacter[], Long>> ids = blizzardDAO.findLegacyLadderIds
        (
            SeasonGenerator.DEFAULT_SEASON_ID + 10,
            new Region[]{Region.US},
            new QueueType[]{QueueType.LOTV_2V2},
            BaseLeague.LeagueType.values(),
            3
        );
        assertTrue(ids.isEmpty());
    }

    @CsvSource
    ({
        "4, 3, 2, 1, TERRAN",
        "1, 4, 3, 2, PROTOSS",
        "1, 2, 4, 3, ZERG",
        "1, 2, 3, 4, RANDOM",

    })
    @ParameterizedTest
    public void testFavoriteRace(int terranGames, int protossGames, int zergGames, int randomGames, Race expectedRace)
    {
        Integer fr = template.query("SELECT * FROM get_favorite_race(?::smallint, ?::smallint, ?::smallint, ?::smallint)", DAOUtils.INT_EXTRACTOR,
            terranGames, protossGames, zergGames, randomGames);
        assertEquals(expectedRace, conversionService.convert(fr, Race.class));
    }

    @Test
    public void testFinderByUpdatedAndIdMax()
    {
        OffsetDateTime now = OffsetDateTime.now();
        assertTrue(playerCharacterDAO
            .find(now.minusYears(1), Long.MAX_VALUE, Set.of(), 2).isEmpty());

        List<PlayerCharacter> batch1 = playerCharacterDAO.find(now, Long.MAX_VALUE, Set.of(), 2);
        assertEquals(2, batch1.size());
        assertEquals(4480, batch1.get(0).getId());
        assertEquals(4479, batch1.get(1).getId());

        List<PlayerCharacter> batch2 = playerCharacterDAO.find(now, batch1.get(1).getId(), Set.of(), 2);
        assertEquals(2, batch2.size());
        assertEquals(4478, batch2.get(0).getId());
        assertEquals(4477, batch2.get(1).getId());
    }

    @Test
    public void testFinderByUpdatedAndIdMaxRegionFilter()
    {
        List<PlayerCharacter> regionFilterBatch = playerCharacterDAO
            .find(OffsetDateTime.now(), Long.MAX_VALUE, Set.of(Region.EU), 2);
        assertEquals(2, regionFilterBatch.size());
        assertEquals(4460, regionFilterBatch.get(0).getId());
        assertEquals(4459, regionFilterBatch.get(1).getId());
    }

    @Test
    public void testFindPopulationStateByIds()
    {
        List<PopulationState> states = populationStateDAO.findByIds(List.of(1, 2));
        assertEquals(2, states.size());
        for(PopulationState state : states)
        {
            assertTrue(state.getId() == 1 || state.getId() == 2);
            assertNotNull(state.getLeagueId());
            assertEquals
            (
                Region.values().length * BaseLeague.LeagueType.values().length * TEAMS_PER_LEAGUE,
                state.getGlobalTeamCount()
            );
            assertEquals
            (
                BaseLeague.LeagueType.values().length * TEAMS_PER_LEAGUE,
                state.getRegionTeamCount()
            );
            assertEquals(TEAMS_PER_LEAGUE, state.getLeagueTeamCount());
        }
    }

    @Test
    public void testFindTeamState()
    {
        ZoneOffset offset = OffsetDateTime.now().getOffset();
        OffsetDateTime seasonStart =
            SeasonGenerator.DEFAULT_SEASON_START.atStartOfDay().atOffset(offset);

        assertEquals(1, ladderTeamStateDAO.find(1L, null).size());
        assertEquals(1, ladderTeamStateDAO.find(1L, seasonStart).size());
        assertEquals(0, ladderTeamStateDAO.find(1L, seasonStart.plusSeconds(1)).size());

    }

}
