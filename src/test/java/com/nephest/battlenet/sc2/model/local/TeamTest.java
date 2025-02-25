// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import static com.nephest.battlenet.sc2.model.BaseLeagueTier.LeagueTierType.FIRST;
import static com.nephest.battlenet.sc2.model.BaseLeagueTier.LeagueTierType.SECOND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.util.TestUtil;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TeamTest
{

    @Test
    public void testUniqueness()
    {
        BaseLeague league = new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.ARRANGED);
        BaseLeague equalLeague = new BaseLeague(BaseLeague.LeagueType.SILVER, QueueType.LOTV_1V1, TeamType.ARRANGED);
        Team team = new Team(0L, 0, Region.EU, league, FIRST, "0", 0,
            0L, 0, 0, 0,0, SC2Pulse.offsetDateTime());
        Team equalTeam = new Team(1L, 0, Region.EU, equalLeague, SECOND, "0", 1,
            1L, 1, 1, 1, 1, SC2Pulse.offsetDateTime());
        equalTeam.setGlobalRank(-1);
        equalTeam.setRegionRank(-1);
        equalTeam.setLeagueRank(-1);

        Team[] notEqualTeams = new Team[]
        {
            new Team(0L, 1, Region.EU, league, FIRST, "0", 0, 0L, 0, 0, 0,0, SC2Pulse.offsetDateTime()),
            new Team(0L, 0, Region.US, league, FIRST, "0", 0, 0L, 0, 0, 0,0, SC2Pulse.offsetDateTime()),
            new Team(0L, 0, Region.EU,
                new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_2V2, TeamType.ARRANGED),
                FIRST, "0", 0, 0L, 0, 0, 0,0, SC2Pulse.offsetDateTime()),
            new Team(0L, 0, Region.EU,
                new BaseLeague(BaseLeague.LeagueType.BRONZE, QueueType.LOTV_1V1, TeamType.RANDOM),
                FIRST, "0", 0, 0L, 0, 0, 0,0, SC2Pulse.offsetDateTime()),
            new Team(0L, 0, Region.EU, league, FIRST, "1", 0, 0L, 0, 0, 0,0, SC2Pulse.offsetDateTime())
        };

        TestUtil.testUniqueness(team, equalTeam, (Object[]) notEqualTeams);
    }

    @Test
    public void testUid()
    {
        assertEquals
        (
            Team.uid(QueueType.LOTV_1V1, TeamType.ARRANGED, Region.EU, "10", 1),
            Team.uid(QueueType.LOTV_1V1, TeamType.ARRANGED, Region.EU, "10", 1)
        );
        assertNotEquals
        (
            Team.uid(QueueType.LOTV_1V1, TeamType.ARRANGED, Region.EU, "10", 1),
            Team.uid(QueueType.LOTV_1V1, TeamType.ARRANGED, Region.EU, "10", 2)
        );
    }

    public static Stream<Arguments> testNestedObjectInitialization()
    {
        return Stream.of(Arguments.of(new Team()));
    }

    @MethodSource
    @ParameterizedTest
    public void testNestedObjectInitialization(Team team)
    {
        assertNotNull(team.getLeague());
    }

}
