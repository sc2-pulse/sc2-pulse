// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyId;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.ConversionService;

@ExtendWith(MockitoExtension.class)
public class ApplicationRoleConnectionTest
{

    @Mock
    private ConversionService conversionService;

    @Mock
    private ConversionService nullConversionService;

    @Test
    public void testStaticCreator()
    {
        when(conversionService.convert(Region.KR, Integer.class)).thenReturn(3);
        when(conversionService.convert(BaseLeague.LeagueType.DIAMOND, Integer.class)).thenReturn(4);
        when(conversionService.convert(Race.PROTOSS, Integer.class)).thenReturn(2);
        String name = "name123";
        Team team = Team.joined
        (
            1L,
            1,
            Region.KR,
            new BaseLeague
            (
                BaseLeague.LeagueType.DIAMOND,
                QueueType.LOTV_1V1,
                TeamType.ARRANGED
            ),
            BaseLeagueTier.LeagueTierType.FIRST,
            TeamLegacyId.trusted("1"),
            1,
            1234L, 1, 1, 1, 1,
            SC2Pulse.offsetDateTime()
        );
        Map<PulseConnectionParameter, List<ConnectionMetaData>> metas =
            new PulseConnectionParameters(nullConversionService).getParameters();

        ApplicationRoleConnection connection = ApplicationRoleConnection.from
        (
            team,
            name,
            Race.PROTOSS,
            metas,
            conversionService
        );
        assertEquals("SC2 Pulse", connection.getPlatformName());
        assertEquals(name, connection.getPlatformUsername());
        assertEquals("3", connection.getMetadata().get("region"));
        assertEquals("2", connection.getMetadata().get("race"));
        assertEquals("4", connection.getMetadata().get("league"));
        assertEquals("1234", connection.getMetadata().get("rating_from"));
        assertEquals("1234", connection.getMetadata().get("rating_to"));
    }

}
