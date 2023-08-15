// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.aligulac.AligulacProPlayer;
import com.nephest.battlenet.sc2.model.aligulac.AligulacProTeamRoot;
import com.nephest.battlenet.sc2.util.TestUtil;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class ProPlayerTest
{

    @Test
    public void testUniqueness()
    {
        ProPlayer proPlayer = new ProPlayer(1L, 2L, "nickname", "name");
        ProPlayer equalProPlayer = new ProPlayer(null, 2L, null, null);
        ProPlayer[] notEqualProPlayers = new ProPlayer[]
        {
            new ProPlayer(1L, 1L, "nickname", "name")
        };
        TestUtil.testUniqueness(proPlayer, equalProPlayer, (Object[]) notEqualProPlayers);
    }

    @Test
    public void whenUpdatingFromAligulacPlayer_thenSanitize()
    {
        LocalDate bd = LocalDate.now().minusYears(10);
        ProPlayer proPlayer = new ProPlayer
        (
            2L,
            3L,
            "tag",
            "name",
            "US",
            LocalDate.now(),
            23456,
            OffsetDateTime.now(),
            1
        );
        AligulacProPlayer aligulacProPlayer = new AligulacProPlayer
        (
            1L,
            "   name   name2 ",
            "   romanized   name   ",
            "  tag  tag2  ",
            "liquipediaName",
            bd,
            "UK",
            12345,
            new AligulacProTeamRoot[]{}
        );
        Assertions.assertThat(ProPlayer.update(proPlayer, aligulacProPlayer))
            .usingRecursiveComparison()
            .ignoringFields("updated")
            .isEqualTo(new ProPlayer(
                2L,
                3L,
                "tag tag2",
                "romanized name",
                "GB",
                bd,
                12345,
                OffsetDateTime.now(),
                1
            ));
    }

}
