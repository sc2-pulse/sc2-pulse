// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static com.nephest.battlenet.sc2.web.service.MapService.MAP_STATS_SKIP_NEW_SEASON_FRAME;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.dao.MapStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.service.EventService;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
public class MapServiceTest
{

    @Mock
    private SeasonDAO seasonDAO;

    @Mock
    private MapStatsDAO mapStatsDAO;

    @Mock
    private VarDAO varDAO;

    @Mock
    private EventService eventService;

    private MapService mapService;

    @BeforeEach
    public void beforeEach()
    {
        when(eventService.getMatchUpdateEvent()).thenReturn(Flux.empty());
        mapService = new MapService(seasonDAO, mapStatsDAO, varDAO, eventService);
    }

    @Test
    public void whenSeasonIsOlderThanSkipDuration_thenSeasonIsOld()
    {
        LocalDate oldDate = LocalDate.now().minusDays(MAP_STATS_SKIP_NEW_SEASON_FRAME.toDays());
        when(seasonDAO.findLast())
            .thenReturn(Optional.of(new Season(1, 1, Region.EU, 2020, 1, oldDate, oldDate.plusDays(1))));
        assertFalse(mapService.seasonIsTooYoung());
    }

    @Test
    public void whenSeasonIsYoungerThanSkipDuration_thenSeasonIsYoung()
    {
        LocalDate youngDate = LocalDate.now()
            .minusDays(MAP_STATS_SKIP_NEW_SEASON_FRAME.minusDays(1).toDays());
        when(seasonDAO.findLast())
            .thenReturn(Optional.of(new Season(1, 1, Region.EU, 2020, 1, youngDate, youngDate.plusDays(1))));
        assertTrue(mapService.seasonIsTooYoung());
    }

}
