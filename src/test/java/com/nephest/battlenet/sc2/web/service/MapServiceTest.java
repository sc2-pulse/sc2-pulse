// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static com.nephest.battlenet.sc2.service.EventService.DEFAULT_FAILURE_HANDLER;
import static com.nephest.battlenet.sc2.web.service.MapService.MAP_STATS_SKIP_NEW_SEASON_FRAME;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.dao.MapStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.MapStatsFilmFrameDAO;
import com.nephest.battlenet.sc2.model.local.dao.MapStatsFilmSpecDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderMapStatsFilmDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.service.EventService;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@ExtendWith(MockitoExtension.class)
public class MapServiceTest
{

    @Mock
    private SeasonDAO seasonDAO;

    @Mock
    private MapStatsDAO mapStatsDAO;

    @Mock
    private MapStatsFilmSpecDAO mapStatsFilmSpecDAO;

    @Mock
    private MapStatsFilmFrameDAO mapStatsFilmFrameDAO;

    @Mock
    private LadderMapStatsFilmDAO ladderMapStatsFilmDAO;

    @Mock
    private VarDAO varDAO;

    @Mock
    private EventService eventService;

    private MapService mapService;

    @BeforeEach
    public void beforeEach()
    {
        when(eventService.getMatchUpdateEvent()).thenReturn(Flux.empty());
        mapService = new MapService
        (
            seasonDAO,
            mapStatsDAO,
            mapStatsFilmSpecDAO,
            mapStatsFilmFrameDAO,
            ladderMapStatsFilmDAO,
            varDAO,
            eventService
        );
        mapService.setMapService(mapService);
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

    @Test
    public void whenCreatingBean_thenUpdateOnMatchUpdateEvent()
    throws InterruptedException
    {
        LocalDate oldDate = LocalDate.now().minusDays(MAP_STATS_SKIP_NEW_SEASON_FRAME.toDays());
        when(seasonDAO.findLast())
            .thenReturn(Optional.of(new Season(1, 1, Region.EU, 2020, 1, oldDate, oldDate.plusDays(1))));

        MatchUpdateContext ctx = new MatchUpdateContext
        (
            Map.of(),
            new UpdateContext(SC2Pulse.instant(), SC2Pulse.instant())
        );
        Sinks.Many<MatchUpdateContext> matchUpdateEvent = Sinks.unsafe()
            .many().multicast().onBackpressureBuffer(2);
        when(eventService.getMatchUpdateEvent()).thenReturn(matchUpdateEvent.asFlux());
        mapService = new MapService
        (
            seasonDAO,
            mapStatsDAO,
            mapStatsFilmSpecDAO,
            mapStatsFilmFrameDAO,
            ladderMapStatsFilmDAO,
            varDAO,
            eventService
        );
        mapService.setMapService(mapService);
        BlockingQueue<UpdateContext> contexts = new ArrayBlockingQueue<>(2);
        Disposable sub = mapService.getUpdateEvent().subscribe(contexts::add);
        try
        {
            matchUpdateEvent.emitNext(ctx, DEFAULT_FAILURE_HANDLER);
            matchUpdateEvent.emitNext(ctx, DEFAULT_FAILURE_HANDLER);
            for (int i = 0; i < 2; i++) contexts.poll(5, TimeUnit.SECONDS);
            verify(mapStatsDAO, times(2)).add(any(), any());
        }
        finally
        {
            sub.dispose();
        }
    }

}
