// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardMatch;
import com.nephest.battlenet.sc2.model.local.Match;
import com.nephest.battlenet.sc2.model.local.MatchParticipant;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.dao.MatchDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchParticipantDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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
    private final VarDAO varDAO;

    private Instant lastUpdated;

    @Autowired
    public MatchService
    (
        BlizzardSC2API api,
        PlayerCharacterDAO playerCharacterDAO,
        MatchDAO matchDAO,
        MatchParticipantDAO matchParticipantDAO,
        VarDAO varDAO
    )
    {
        this.api = api;
        this.playerCharacterDAO = playerCharacterDAO;
        this.matchDAO = matchDAO;
        this.matchParticipantDAO = matchParticipantDAO;
        this.varDAO = varDAO;
    }

    @PostConstruct
    public void init()
    {
        //catch exceptions to allow service autowiring for tests
        try {
            loadLastUpdated();
        }
        catch(RuntimeException ex) {
            LOG.warn(ex.getMessage(), ex);
        }
    }

    private void loadLastUpdated()
    {
        String updatesVar = varDAO.find("match.updated").orElse(null);
        if(updatesVar == null || updatesVar.isEmpty()) {
            lastUpdated = null;
            return;
        }

        lastUpdated = Instant.ofEpochMilli(Long.parseLong(updatesVar));
        LOG.debug("Loaded last updated: {}", lastUpdated);
    }

    private void updateLastUpdated()
    {
        lastUpdated = Instant.now();
        varDAO.merge("match.updated", String.valueOf(lastUpdated.toEpochMilli()));
    }

    @Transactional
    public void update()
    {
        if(lastUpdated == null) {
            updateLastUpdated();
            return;
        }

        AtomicInteger count = new AtomicInteger(0);
        api.getMatches(playerCharacterDAO.findRecentlyActiveCharacters(OffsetDateTime.ofInstant(lastUpdated, ZoneId.systemDefault())))
            .flatMap(m->Flux.fromArray(m.getT1().getMatches())
                .zipWith(Flux.fromStream(Stream.iterate(m.getT2(), i->m.getT2()))))
            .buffer(BATCH_SIZE)
            .doOnNext(b->count.getAndAdd(b.size()))
            .toStream(2)
            .forEach(this::saveMatches);
        matchDAO.removeExpired();
        updateLastUpdated();
        LOG.info("Saved {} matches", count.get());
    }

    private void saveMatches(List<Tuple2<BlizzardMatch, PlayerCharacter>> matches)
    {
        Match[] matchBatch = new Match[matches.size()];
        MatchParticipant[] participantBatch = new MatchParticipant[matches.size()];
        List<Tuple3<Match, BaseMatch.Decision, PlayerCharacter>> meta = new ArrayList<>();
        for(int i = 0; i < matches.size(); i++)
        {
            Tuple2<BlizzardMatch, PlayerCharacter> match = matches.get(i);
            Match localMatch = Match.of(match.getT1());
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
