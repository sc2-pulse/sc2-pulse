// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardMatch;
import com.nephest.battlenet.sc2.model.local.Match;
import com.nephest.battlenet.sc2.model.local.MatchParticipant;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.dao.MatchDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchParticipantDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import com.nephest.battlenet.sc2.util.MiscUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;


@Service
public class MatchService
{

    private static final Logger LOG = LoggerFactory.getLogger(MatchService.class);
    public static final int BATCH_SIZE = 1000;

    private final BlizzardSC2API api;
    private final MatchDAO matchDAO;
    private final MatchParticipantDAO matchParticipantDAO;
    private final PlayerCharacterDAO playerCharacterDAO;
    private final SeasonDAO seasonDAO;
    private final PostgreSQLUtils postgreSQLUtils;
    private final ExecutorService dbExecutorService;
    private final ConcurrentLinkedQueue<Set<PlayerCharacter>> failedCharacters = new ConcurrentLinkedQueue<>();

    @Autowired @Lazy
    private MatchService matchService;

    @Autowired
    public MatchService
    (
        BlizzardSC2API api,
        PlayerCharacterDAO playerCharacterDAO,
        MatchDAO matchDAO,
        MatchParticipantDAO matchParticipantDAO,
        SeasonDAO seasonDAO,
        PostgreSQLUtils postgreSQLUtils,
        @Qualifier("dbExecutorService") ExecutorService dbExecutorService
    )
    {
        this.api = api;
        this.playerCharacterDAO = playerCharacterDAO;
        this.matchDAO = matchDAO;
        this.matchParticipantDAO = matchParticipantDAO;
        this.seasonDAO = seasonDAO;
        this.postgreSQLUtils = postgreSQLUtils;
        this.dbExecutorService = dbExecutorService;
    }

    public void update(UpdateContext updateContext, Region... regions)
    {
        if(updateContext.getExternalUpdate() == null || updateContext.getInternalUpdate() == null) return;

        //Active players can't be updated retroactively, so there is no reason to sync with other services here
        int matchCount = saveMatches(updateContext.getInternalUpdate(), regions);
        postgreSQLUtils.vacuumAnalyze();
        int identified = matchParticipantDAO.identify(
            seasonDAO.getMaxBattlenetId(),
            /*
                Matches are fetched retroactively, some of them can happen before the lastUpdated instant.
                Try to catch these matches by moving the start instant back in time.
             */
            OffsetDateTime.ofInstant(updateContext.getExternalUpdate(), ZoneOffset.systemDefault()).minusMinutes(MatchParticipantDAO.IDENTIFICATION_FRAME_MINUTES));
        matchDAO.removeExpired();
        LOG.info("Saved {} matches({} identified)", matchCount, identified);
    }

    private int saveMatches(Instant lastUpdated, Region... regions)
    {
        int r1 = saveFailedMatches();
        LOG.debug("Saved {} previously failed matches", r1);
        //clear here to avoid unbound retries of the same characters
        return r1 + saveMatches(playerCharacterDAO
            .findRecentlyActiveCharacters(OffsetDateTime.ofInstant(lastUpdated, ZoneId.systemDefault()), regions));
    }

    private int saveFailedMatches()
    {
        int i = 0;
        Set<PlayerCharacter> chars;
        while((chars = failedCharacters.poll()) != null)
        {
            LOG.debug("Retrying {} previously failed matches", chars.size());
            i += saveMatches(chars);
        }
        return i;
    }

    private int saveMatches(Iterable<? extends PlayerCharacter> characters)
    {
        List<Future<?>> dbTasks = new ArrayList<>();
        AtomicInteger count = new AtomicInteger(0);
        Set<PlayerCharacter> errors = new HashSet<>();
        api.getMatches(characters, errors)
            .flatMap(m->Flux.fromArray(m.getT1().getMatches())
                .zipWith(Flux.fromStream(Stream.iterate(m.getT2(), i->m.getT2()))))
            .sequential()
            .buffer(BATCH_SIZE)
            .doOnNext(b->count.getAndAdd(b.size()))
            .toStream()
            .forEach(m->dbTasks.add(dbExecutorService.submit(()->matchService.saveMatches(m))));
        MiscUtil.awaitAndLogExceptions(dbTasks, true);
        failedCharacters.add(errors);
        return count.get();
    }

    @Transactional
    protected void saveMatches(List<Tuple2<BlizzardMatch, PlayerCharacter>> matches)
    {
        Match[] matchBatch = new Match[matches.size()];
        MatchParticipant[] participantBatch = new MatchParticipant[matches.size()];
        List<Tuple3<Match, BaseMatch.Decision, PlayerCharacter>> meta = new ArrayList<>();
        for(int i = 0; i < matches.size(); i++)
        {
            Tuple2<BlizzardMatch, PlayerCharacter> match = matches.get(i);
            Match localMatch = Match.of(match.getT1(), match.getT2().getRegion());
            matchBatch[i] = localMatch;
            meta.add(Tuples.of(localMatch, match.getT1().getDecision(), match.getT2()));
        }
        matchDAO.merge(matchBatch);
        for(int i = 0; i < meta.size(); i++)
        {
            Tuple3<Match, BaseMatch.Decision, PlayerCharacter> participant = meta.get(i);
            participantBatch[i] = new MatchParticipant
            (
                participant.getT1().getId(),
                participant.getT3().getId(),
                participant.getT2()
            );
        }
        matchParticipantDAO.merge(participantBatch);
        LOG.debug("Saved {} matches", matches.size());
    }

}
