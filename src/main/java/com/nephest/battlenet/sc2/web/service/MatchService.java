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
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final SeasonDAO seasonDAO;

    @Autowired
    public MatchService
    (
        BlizzardSC2API api,
        PlayerCharacterDAO playerCharacterDAO,
        MatchDAO matchDAO,
        MatchParticipantDAO matchParticipantDAO,
        SeasonDAO seasonDAO
    )
    {
        this.api = api;
        this.playerCharacterDAO = playerCharacterDAO;
        this.matchDAO = matchDAO;
        this.matchParticipantDAO = matchParticipantDAO;
        this.seasonDAO = seasonDAO;
    }

    @Transactional
    public void update(Instant lastUpdated)
    {
        if(lastUpdated == null) return;

        AtomicInteger count = new AtomicInteger(0);
        api.getMatches(playerCharacterDAO.findRecentlyActiveCharacters(OffsetDateTime.ofInstant(lastUpdated, ZoneId.systemDefault())))
            .flatMap(m->Flux.fromArray(m.getT1().getMatches())
                .zipWith(Flux.fromStream(Stream.iterate(m.getT2(), i->m.getT2()))))
            .filter(m->
                m.getT1().getType() == BaseMatch.MatchType._1V1
                || m.getT1().getType() == BaseMatch.MatchType._2V2
                || m.getT1().getType() == BaseMatch.MatchType._3V3
                || m.getT1().getType() == BaseMatch.MatchType._4V4
                || m.getT1().getType() == BaseMatch.MatchType.ARCHON)
            .buffer(BATCH_SIZE)
            .doOnNext(b->count.getAndAdd(b.size()))
            .toStream(2)
            .forEach(this::saveMatches);
        int identified = matchParticipantDAO.identify(
            seasonDAO.getMaxBattlenetId(),
            /*
                Matches are fetched retroactively, some of them can happen before the lastUpdated instant.
                Try to catch these matches by moving the start instant back in time.
             */
            OffsetDateTime.ofInstant(lastUpdated, ZoneOffset.systemDefault()).minusMinutes(MatchParticipantDAO.IDENTIFICATION_FRAME_MINUTES));
        matchDAO.removeExpired();
        LOG.info("Saved {} matches({} identified)", count.get(), identified);
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
