// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.sc2arcade.SC2ArcadeLadderCharacter;
import com.nephest.battlenet.sc2.model.sc2arcade.SC2ArcadeLadderGame;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.web.service.SC2ArcadeMatchService.GameStatus;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class SC2ArcadeMatchServiceTest
{

    private static final OffsetDateTime MIN_END_DATE =
        SC2Pulse.offsetDateTime(2026, 11, 1);

    @CsvSource(nullValues = "null", value =
    {
        "LADDER_1V1, 0, map, 1=win 2=loss",
        "LADDER_1V1, 0, map, 1=loss 2=win",
        "LADDER_1V1, 0, map, 1=tie 2=tie",
        "MISSING_DATA, null, map, 1=win 2=loss",
        "MISSING_DATA, 0, null, 1=win 2=loss",
        "EXPIRED, -1, map, 1=win 2=loss",
        "INVALID_PLAYERS, 0, map, 1=win",
        "INVALID_PLAYERS, 0, map, 1=win 1=loss",
        "INVALID_PLAYERS, 0, map, 1=win 2=loss 3=loss",
        "INVALID_PLAYERS, 0, map, 1=win 2=win 3=loss 4=loss",
        "INVALID_PLAYERS, 0, map, 1=win x=loss",
        "HAS_BAILER, 0, map, 1=win 2=bailer",
        "INVALID_RESULTS, 0, map, 1=win 2=win",
        "INVALID_RESULTS, 0, map, 1=win 2=tie",
        "INVALID_RESULTS, 0, map, 1=win 2=disagree",
        "INVALID_RESULTS, 0, map, 1=win 2=",
        "INVALID_RESULTS, 0, map, 1=win 2=unknown",
        "NOT_LADDER_MAP, 0, custom, 1=win 2=loss"
    })
    @ParameterizedTest
    public void testGameStatus
    (
        GameStatus expectedStatus,
        Integer endDateOffsetInSeconds,
        String mapName,
        String playerResults
    )
    {
        SC2ArcadeLadderGame game = new SC2ArcadeLadderGame
        (
            endDateOffsetInSeconds == null
                ? null
                : MIN_END_DATE.plusSeconds(endDateOffsetInSeconds),
            100,
            new SC2ArcadeLadderGame.GameMap(mapName),
            Arrays.stream(playerResults.split(" "))
                .map(SC2ArcadeMatchServiceTest::parsePlayer)
                .toList()
        );
        assertEquals(expectedStatus, SC2ArcadeMatchService.getStatus(
            game, Region.EU, Set.of("map"), MIN_END_DATE));
    }

    private static SC2ArcadeLadderGame.Player parsePlayer
    (String playerResult)
    {
        List<String> idAndResult = List.of(playerResult.split("=", -1));
        return new SC2ArcadeLadderGame.Player
        (
            new SC2ArcadeLadderCharacter
            (
                "EU",
                1,
                idAndResult.get(0),
                "name",
                null
            ),
            idAndResult.get(1)
        );
    }

}
