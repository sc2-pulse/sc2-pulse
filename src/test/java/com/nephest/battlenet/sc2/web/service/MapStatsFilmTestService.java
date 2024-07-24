// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static com.nephest.battlenet.sc2.web.service.MapService.FILM_FRAME_DURATION;

import com.nephest.battlenet.sc2.model.local.SeasonGenerator;
import com.nephest.battlenet.sc2.model.local.dao.LeagueStatsDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchParticipantDAO;
import com.nephest.battlenet.sc2.model.local.dao.PopulationStateDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamStateDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.service.EventService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MapStatsFilmTestService
{

    public static final int FRAME_NUMBER = 2;
    public static final long FRAME_OFFSET = FILM_FRAME_DURATION.toSeconds() * FRAME_NUMBER;
    
    private final TeamDAO teamDAO;
    private final TeamStateDAO teamStateDAO;
    private final MatchParticipantDAO matchParticipantDAO;
    private final MatchDAO matchDAO;
    private final PopulationStateDAO populationStateDAO;
    private final LeagueStatsDAO leagueStatsDAO;
    private final SeasonGenerator seasonGenerator;
    private final EventService eventService;
    private final MapService mapService;
    private final StatsService statsService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public MapStatsFilmTestService
    (
        TeamDAO teamDAO,
        TeamStateDAO teamStateDAO,
        MatchParticipantDAO matchParticipantDAO,
        MatchDAO matchDAO,
        PopulationStateDAO populationStateDAO,
        LeagueStatsDAO leagueStatsDAO,
        SeasonGenerator seasonGenerator,
        EventService eventService,
        MapService mapService,
        StatsService statsService,
        JdbcTemplate jdbcTemplate
    )
    {
        this.teamDAO = teamDAO;
        this.teamStateDAO = teamStateDAO;
        this.matchParticipantDAO = matchParticipantDAO;
        this.matchDAO = matchDAO;
        this.populationStateDAO = populationStateDAO;
        this.leagueStatsDAO = leagueStatsDAO;
        this.seasonGenerator = seasonGenerator;
        this.eventService = eventService;
        this.mapService = mapService;
        this.statsService = statsService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public BlockingQueue<UpdateContext> generateFilms(Function<OffsetDateTime, List<Long>> matchGenerator)
    throws InterruptedException
    {
        OffsetDateTime startFrom = SC2Pulse.offsetDateTime().plusMonths(1);
        Instant mucInstant = startFrom.plusDays(1).toInstant();
        seasonGenerator.generateDefaultSeason(1200, true);
        statsService.createLeaguesAndTiers(SeasonGenerator.defaultSeason());
        leagueStatsDAO.calculateForSeason(SeasonGenerator.DEFAULT_SEASON_ID);
        populationStateDAO.takeSnapshot(Set.of(SeasonGenerator.DEFAULT_SEASON_ID));
        teamDAO.updateRanks(SeasonGenerator.DEFAULT_SEASON_ID);
        List<Long> teamIds = matchGenerator.apply(startFrom);
        jdbcTemplate.update("DELETE FROM team_state");

        BlockingQueue<UpdateContext> updateContexts = new ArrayBlockingQueue<>(1);
        mapService.getUpdateEvent().subscribe(updateContexts::add);
        teamStateDAO.takeSnapshot(teamIds, startFrom);
        seasonGenerator.takeTeamSnapshot(teamIds, startFrom, FRAME_OFFSET, 1);
        seasonGenerator.takeTeamSnapshot(teamIds, startFrom, FRAME_OFFSET, 2);
        matchParticipantDAO.identify(SeasonGenerator.DEFAULT_SEASON_ID, startFrom);
        matchDAO.updateDuration(startFrom);
        eventService.createMatchUpdateEvent(new MatchUpdateContext(
            Map.of(), new UpdateContext(mucInstant, mucInstant)));
        updateContexts.poll(5, TimeUnit.SECONDS);
        return updateContexts;
    }

}
