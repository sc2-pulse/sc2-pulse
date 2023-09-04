// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.local.InstantVar;
import com.nephest.battlenet.sc2.model.local.dao.MapStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchParticipantDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.service.EventService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Schedulers;

@Service
public class MapService
{

    public static final Duration MAP_STATS_DEFAULT_UPDATE_FRAME = Duration.ofMinutes(60);
    public static final Duration MAP_STATS_SKIP_NEW_SEASON_FRAME = Duration.ofDays(8);

    private final SeasonDAO seasonDAO;
    private final MapStatsDAO mapStatsDAO;
    private final InstantVar mapStatsInstant;

    @Autowired
    public MapService
    (
        SeasonDAO seasonDAO,
        MapStatsDAO mapStatsDAO,
        VarDAO varDAO,
        EventService eventService
    )
    {
        this.seasonDAO = seasonDAO;
        this.mapStatsDAO = mapStatsDAO;
        mapStatsInstant = new InstantVar(varDAO, "ladder.stats.map.timestamp", false);
        Instant defaultMapStatsInstant = Instant.now().minusSeconds
        (
            MatchParticipantDAO.IDENTIFICATION_FRAME_MINUTES * 60
            + MAP_STATS_DEFAULT_UPDATE_FRAME.toSeconds()
        );
        mapStatsInstant.tryLoad(defaultMapStatsInstant);
        subscribeToEvents(eventService);
    }

    private void subscribeToEvents(EventService eventService)
    {
        eventService.getMatchUpdateEvent()
            .publishOn(Schedulers.boundedElastic())
            .subscribe(this::update);
    }

    public OffsetDateTime getMapStatsStart()
    {
        return OffsetDateTime.ofInstant(mapStatsInstant.getValue(), ZoneId.systemDefault());
    }

    public OffsetDateTime getMapStatsEnd(UpdateContext matchUpdateContext)
    {
        return OffsetDateTime.ofInstant(matchUpdateContext.getExternalUpdate(), ZoneOffset.systemDefault())
            .minusMinutes(MatchParticipantDAO.IDENTIFICATION_FRAME_MINUTES);
    }

    public boolean seasonIsTooYoung()
    {
        return seasonDAO.findLast().orElseThrow().getStart()
            .plusDays(MAP_STATS_SKIP_NEW_SEASON_FRAME.toDays()).isAfter(LocalDate.now());
    }

    private void update(MatchUpdateContext updateContext)
    {
        OffsetDateTime to = getMapStatsEnd(updateContext.getUpdateContext());
        //skipping because the ladder is very volatile(top% leagues) at the beginning of the new season
        if(seasonIsTooYoung())
        {
            mapStatsInstant.setValueAndSave(to.toInstant());
            return;
        }

        OffsetDateTime from = getMapStatsStart();
        if(from.isAfter(to)) return;
        /*
            Map stats are incremental stats, preemptively update the var to prevent double
            calculation in exceptional cases. Some stats may be lost this way, but this
            guarantees that existing stats are 100% valid.
         */
        mapStatsInstant.setValueAndSave(to.toInstant());
        mapStatsDAO.add(from, to);
    }

}
