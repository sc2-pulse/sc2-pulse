// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import static com.nephest.battlenet.sc2.model.BaseLeague.LeagueType.BRONZE;
import static com.nephest.battlenet.sc2.model.BaseLeague.LeagueType.DIAMOND;
import static com.nephest.battlenet.sc2.model.BaseLeague.LeagueType.SILVER;
import static com.nephest.battlenet.sc2.model.BaseLeagueTier.LeagueTierType.FIRST;
import static com.nephest.battlenet.sc2.model.BaseLeagueTier.LeagueTierType.THIRD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nephest.battlenet.sc2.config.DatabaseTestConfig;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.Version;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.LeagueTier;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = DatabaseTestConfig.class)
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class SeasonStructureIT
{

    @Autowired
    private SeasonDAO seasonDAO;

    @Autowired
    private LeagueDAO leagueDAO;

    @Autowired
    private LeagueTierDAO leagueTierDAO;

    private static List<Season> SEASONS = new ArrayList<>();
    private static final List<League> LEAGUES = new ArrayList<>();
    private static final List<LeagueTier> TIERS = new ArrayList<>();

    @BeforeAll
    public static void beforeAll
    (
        @Autowired DataSource dataSource,
        @Autowired SeasonDAO seasonDAO,
        @Autowired LeagueDAO leagueDAO,
        @Autowired LeagueTierDAO leagueTierDAO
    )
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
        OffsetDateTime seasonStart = SC2Pulse.offsetDateTime();
        SEASONS = Arrays.stream(Region.values())
            .map(region->new Season(null, 1, region, seasonStart.getYear(), 1,
                seasonStart.plusMonths(region.ordinal()),
                seasonStart.plusMonths(region.ordinal() + 1)))
            .map(seasonDAO::create)
            .collect(Collectors.toList());

        for(Season season : SEASONS)
            for(QueueType queueType : QueueType.getTypes(Version.LOTV))
                for(TeamType teamType : TeamType.values())
                    for(BaseLeague.LeagueType leagueType : BaseLeague.LeagueType.values())
                        LEAGUES.add(new League(null,
                            season.getId(), leagueType, queueType, teamType));
        LEAGUES.forEach(leagueDAO::create);

        for(League league : LEAGUES)
            for(BaseLeagueTier.LeagueTierType type : BaseLeagueTier.LeagueTierType.values())
                TIERS.add(new LeagueTier(null, league.getId(), type,
                    Integer.parseInt("" + league.getId() + type.ordinal()),
                    Integer.parseInt("" + league.getId() + type.ordinal()) + 1));
        TIERS.forEach(leagueTierDAO::create);
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

    public static Stream<Arguments> testFindLeaguesByUids()
    {
        return Stream.of
        (
            Arguments.of
            (
                Set.of(1, 3),
                EnumSet.of(QueueType.LOTV_1V1, QueueType.LOTV_2V2),
                TeamType.ARRANGED,
                EnumSet.of(SILVER, DIAMOND),
                List.of
                (
                    2, 5,
                    16, 19,
                    142, 145,
                    156, 159
                )
            ),
            Arguments.of
            (
                Set.of(1),
                EnumSet.of(QueueType.LOTV_1V1, QueueType.LOTV_2V2),
                TeamType.ARRANGED,
                EnumSet.of(SILVER, DIAMOND),
                List.of
                (
                    2, 5,
                    16, 19
                )
            ),
            Arguments.of
            (
                Set.of(1),
                Set.of(),
                TeamType.ARRANGED,
                EnumSet.of(SILVER, DIAMOND),
                List.of
                (
                    2, 5,
                    16, 19,
                    30, 33,
                    44, 47,
                    58, 61
                )
            ),
            Arguments.of
            (
                Set.of(1),
                EnumSet.of(QueueType.LOTV_1V1),
                TeamType.ARRANGED,
                Set.of(),
                List.of(1, 2, 3, 4, 5, 6, 7)
            ),
            Arguments.of
            (
                Set.of(1),
                EnumSet.of(QueueType.LOTV_1V1),
                null,
                EnumSet.of(BRONZE),
                List.of(1, 8)
            ),
            Arguments.of
            (
                Set.of(1),
                Set.of(),
                null,
                Set.of(),
                IntStream.range(1, 71)
                    .boxed()
                    .collect(Collectors.toList())
            )
        );
    }

    @MethodSource
    @ParameterizedTest
    public void testFindLeaguesByUids
    (
        Set<Integer> seasonIds,
        Set<QueueType> queues,
        TeamType teamType,
        Set<BaseLeague.LeagueType> leagueTypes,
        List<Integer> expectedIds
    )
    {
        List<Integer> found = leagueDAO.find(seasonIds, queues, teamType, leagueTypes).stream()
            .map(League::getId)
            .sorted()
            .collect(Collectors.toList());
        assertEquals(expectedIds, found);
    }


    public static Stream<Arguments> whenFindLeaguesByUidsAndSeasonIdsIsEmptyAndOtherDataIsPresent_thenThrowException()
    {
        return Stream.of
        (
            Arguments.of(EnumSet.of(QueueType.LOTV_1V1), null, Set.of()),
            Arguments.of(Set.of(), TeamType.ARRANGED, Set.of()),
            Arguments.of(Set.of(), null, EnumSet.of(BRONZE))
        );
    }

    @MethodSource
    @ParameterizedTest
    public void whenFindLeaguesByUidsAndSeasonIdsIsEmptyAndOtherDataIsPresent_thenThrowException
    (
        Set<QueueType> queues,
        TeamType teamType,
        Set<BaseLeague.LeagueType> leagueTypes
    )
    {
        assertThrows
        (
            IllegalArgumentException.class,
            ()->leagueDAO.find(Set.of(), queues, teamType, leagueTypes),
            "Missing seasonIds"
        );
    }

    public static Stream<Arguments> testFindLeagueTiersByUids()
    {
        return Stream.of
        (
            Arguments.of
            (
                Set.of(1, 3),
                EnumSet.of(FIRST, THIRD),
                List.of(1, 3, 7, 9)
            ),
            Arguments.of
            (
                Set.of(1, 3),
                Set.of(),
                List.of(1, 2, 3, 7, 8, 9)
            )
        );
    }

    @MethodSource
    @ParameterizedTest
    public void testFindLeagueTiersByUids
    (
        Set<Integer> leagueIds,
        Set<BaseLeagueTier.LeagueTierType> types,
        List<Integer> expectedIds
    )
    {
        List<Integer> found = leagueTierDAO.find(leagueIds, types).stream()
            .map(LeagueTier::getId)
            .sorted()
            .collect(Collectors.toList());
        assertEquals(expectedIds, found);
    }

    @Test
    public void whenFindLeagueTierByUidsAndLeagueIdsIsEmptyAndTypesIsNotEmpty_thenTrowException()
    {
        assertThrows
        (
            IllegalArgumentException.class,
            ()->leagueTierDAO.find(Set.of(), EnumSet.of(FIRST)),
            "Missing leagueIds"
        );
    }

    @Test
    public void testFindLeagueTiersByIds()
    {
        List<LeagueTier> tiers = leagueTierDAO.findByIds(Set.of(1, 2, 4));
        tiers.sort(Comparator.comparing(LeagueTier::getId));
        Assertions.assertThat(tiers)
            .usingRecursiveComparison()
            .isEqualTo(List.of(
                new LeagueTier(1, 1, BaseLeagueTier.LeagueTierType.FIRST, 10, 11),
                new LeagueTier(2, 1, BaseLeagueTier.LeagueTierType.SECOND, 11, 12),
                new LeagueTier(4, 2, BaseLeagueTier.LeagueTierType.FIRST, 20, 21)
            ));
    }

}
