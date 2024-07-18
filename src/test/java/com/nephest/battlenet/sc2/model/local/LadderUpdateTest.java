// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.util.TestUtil;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

public class LadderUpdateTest
{

    @Test
    public void testUniqueness()
    {
        OffsetDateTime equalOdt = SC2Pulse.offsetDateTime();
        Duration equalDuration = Duration.ofSeconds(1);
        TestUtil.testUniqueness
        (
            new LadderUpdate
            (
                Region.EU,
                QueueType.LOTV_1V1,
                BaseLeague.LeagueType.BRONZE,
                equalOdt,
                equalDuration
            ),
            new LadderUpdate
            (
                Region.EU,
                QueueType.LOTV_1V1,
                BaseLeague.LeagueType.BRONZE,
                equalOdt,
                equalDuration.plusSeconds(1)
            ),
            (Object[]) new LadderUpdate[]
            {
                new LadderUpdate
                (
                    Region.US,
                    QueueType.LOTV_1V1,
                    BaseLeague.LeagueType.BRONZE,
                    equalOdt,
                    equalDuration
                ),
                new LadderUpdate
                (
                    Region.EU,
                    QueueType.LOTV_2V2,
                    BaseLeague.LeagueType.BRONZE,
                    equalOdt,
                    equalDuration
                ),
                new LadderUpdate
                (
                    Region.EU,
                    QueueType.LOTV_1V1,
                    BaseLeague.LeagueType.SILVER,
                    equalOdt,
                    equalDuration
                ),
                new LadderUpdate
                (
                    Region.EU,
                    QueueType.LOTV_1V1,
                    BaseLeague.LeagueType.BRONZE,
                    equalOdt.plusSeconds(1),
                    equalDuration
                )
            }
        );
    }

}
