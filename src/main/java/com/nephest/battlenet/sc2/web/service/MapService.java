// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static com.nephest.battlenet.sc2.model.Race.PROTOSS;
import static com.nephest.battlenet.sc2.model.Race.RANDOM;
import static com.nephest.battlenet.sc2.model.Race.TERRAN;
import static com.nephest.battlenet.sc2.model.Race.ZERG;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.InstantVar;
import com.nephest.battlenet.sc2.model.local.MapStatsFilmSpec;
import com.nephest.battlenet.sc2.model.local.MatchUp;
import com.nephest.battlenet.sc2.model.local.dao.MapStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.MapStatsFilmFrameDAO;
import com.nephest.battlenet.sc2.model.local.dao.MapStatsFilmSpecDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchParticipantDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMapStatsFilm;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderMapStatsFilmDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.service.EventService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Service
public class MapService
{

    public static final Duration MAP_STATS_DEFAULT_UPDATE_FRAME = Duration.ofMinutes(60);
    public static final Duration MAP_STATS_SKIP_NEW_SEASON_FRAME = Duration.ofDays(8);

    public static final Set<MatchUp> MATCH_UPS = Set.of
    (
        new MatchUp(PROTOSS, TERRAN),
        new MatchUp(ZERG, PROTOSS),
        new MatchUp(TERRAN, ZERG),

        new MatchUp(RANDOM, TERRAN),
        new MatchUp(RANDOM, PROTOSS),
        new MatchUp(RANDOM, ZERG)
    );
    public static final Duration FILM_FRAME_DURATION = Duration.ofMinutes(1);

    private final SeasonDAO seasonDAO;
    private final MapStatsDAO mapStatsDAO;
    private final MapStatsFilmSpecDAO mapStatsFilmSpecDAO;
    private final MapStatsFilmFrameDAO mapStatsFilmFrameDAO;
    private final LadderMapStatsFilmDAO ladderMapStatsFilmDAO;
    private final InstantVar mapStatsInstant;
    private final Sinks.Many<UpdateContext> updateEvent = Sinks.unsafe().many().multicast().onBackpressureBuffer(1);
    private boolean dbInitialized = false;

    @Autowired @Lazy
    private MapService mapService;

    @Autowired
    public MapService
    (
        SeasonDAO seasonDAO,
        MapStatsDAO mapStatsDAO,
        MapStatsFilmSpecDAO mapStatsFilmSpecDAO,
        MapStatsFilmFrameDAO mapStatsFilmFrameDAO,
        LadderMapStatsFilmDAO ladderMapStatsFilmDAO,
        VarDAO varDAO,
        EventService eventService
    )
    {
        this.seasonDAO = seasonDAO;
        this.mapStatsDAO = mapStatsDAO;
        this.mapStatsFilmSpecDAO = mapStatsFilmSpecDAO;
        this.mapStatsFilmFrameDAO = mapStatsFilmFrameDAO;
        this.ladderMapStatsFilmDAO = ladderMapStatsFilmDAO;
        mapStatsInstant = new InstantVar(varDAO, "ladder.stats.map.timestamp", false);
        Instant defaultMapStatsInstant = SC2Pulse.instant().minusSeconds
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
            .flatMap(muc->WebServiceUtil.getOnErrorLogAndSkipMono(
                WebServiceUtil.blockingRunnable(this::initDb)).thenReturn(muc))
            .flatMap(muc->WebServiceUtil.getOnErrorLogAndSkipMono(
                WebServiceUtil.blockingCallable(()->mapService.update(muc))))
            .doOnNext(uc->updateEvent.emitNext(uc, EventService.DEFAULT_FAILURE_HANDLER))
            .subscribe();
    }

    protected MapService getMapService()
    {
        return mapService;
    }

    protected void setMapService(MapService mapService)
    {
        this.mapService = mapService;
    }

    protected InstantVar getMapStatsInstant()
    {
        return mapStatsInstant;
    }

    public Flux<UpdateContext> getUpdateEvent()
    {
        return updateEvent.asFlux();
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

    @CacheEvict
    (
        value = "fqdn-map-stats",
        keyGenerator = "fqdnSimpleKeyGenerator",
        allEntries = true,
        condition = "#result != null"
    )
    public UpdateContext update(MatchUpdateContext updateContext)
    {
        OffsetDateTime to = getMapStatsEnd(updateContext.getUpdateContext());
        //skipping because the ladder is very volatile(top% leagues) at the beginning of the new season
        if(seasonIsTooYoung())
        {
            mapStatsInstant.setValueAndSave(to.toInstant());
            return null;
        }

        OffsetDateTime from = getMapStatsStart();
        if(from.isAfter(to)) return null;
        /*
            Map stats are incremental stats, preemptively update the var to prevent double
            calculation in exceptional cases. Some stats may be lost this way, but this
            guarantees that existing stats are 100% valid.
         */
        mapStatsInstant.setValueAndSave(to.toInstant());
        mapStatsDAO.add(from, to);
        mapStatsFilmFrameDAO.add(from, to, FILM_FRAME_DURATION);
        return new UpdateContext(from.toInstant(), to.toInstant());
    }

    private void initDb()
    {
        if(dbInitialized) return;

        createMapStatsFilmSpecs();
        dbInitialized = true;
    }

    protected void setDbInitialized(boolean dbInitialized)
    {
        this.dbInitialized = dbInitialized;
    }

    private void createMapStatsFilmSpecs()
    {
        Set<MapStatsFilmSpec> specs
            = Set.copyOf(mapStatsFilmSpecDAO.find(MATCH_UPS, FILM_FRAME_DURATION));
        if(specs.size() == MATCH_UPS.size()) return;

        MATCH_UPS.stream()
            .map(mu->new MapStatsFilmSpec(null,
                mu.getRaces().get(0), mu.getVersusRaces().get(0), FILM_FRAME_DURATION))
            .filter(spec->!specs.contains(spec))
            .forEach(mapStatsFilmSpecDAO::create);
    }

    private Set<MatchUp> getMatchUps(Set<Race> races)
    {
        return MATCH_UPS.stream()
            .filter
            (
                matchUp->races.containsAll(matchUp.getRaces())
                    || races.containsAll(matchUp.getVersusRaces())
            )
            .collect(Collectors.toSet());
    }

    @Cacheable(value = "fqdn-map-stats", keyGenerator = "fqdnSimpleKeyGenerator")
    public LadderMapStatsFilm findFilm
    (
        Set<Race> races,
        Duration frameDuration,
        Integer frameNumberMax,
        int season,
        Set<Region> regions,
        QueueType queue,
        TeamType teamType,
        BaseLeague.LeagueType league,
        BaseLeagueTier.LeagueTierType tier
    )
    {
        if(queue != QueueType.LOTV_1V1)
            throw new IllegalArgumentException("Unsupported queue: " + queue);
        if(!frameDuration.equals(FILM_FRAME_DURATION))
            throw new IllegalArgumentException("Unsupported frame duration: " + frameDuration);
        if(frameNumberMax != null && frameNumberMax < 1)
            throw new IllegalArgumentException("Max frame number should be more than 0");

        return ladderMapStatsFilmDAO.find
        (
            getMatchUps(races),
            frameDuration,
            frameNumberMax,
            season,
            regions,
            queue,
            teamType,
            league,
            tier
        );
    }

}
