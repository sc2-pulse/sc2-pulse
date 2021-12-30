// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardMatch;
import com.nephest.battlenet.sc2.model.local.*;
import com.nephest.battlenet.sc2.model.local.dao.*;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import com.nephest.battlenet.sc2.util.MiscUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Validator;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Service
public class MatchService
{

    private static final Logger LOG = LoggerFactory.getLogger(MatchService.class);
    public static final int BATCH_SIZE = 1000;
    public static final int FAILED_MATCHES_MAX = 100;
    public static final QueueType WEB_QUEUE_TYPE = QueueType.LOTV_1V1;
    public static final TeamType WEB_TEAM_TYPE = TeamType.ARRANGED;
    public static final int WEB_CHARACTER_COUNT = 500;

    private final BlizzardSC2API api;
    private final MatchDAO matchDAO;
    private final MatchParticipantDAO matchParticipantDAO;
    private final PlayerCharacterDAO playerCharacterDAO;
    private final SeasonDAO seasonDAO;
    private final SC2MapDAO mapDAO;
    private final PostgreSQLUtils postgreSQLUtils;
    private final ExecutorService dbExecutorService;
    private final Predicate<BlizzardMatch> validationPredicate;
    private final ConcurrentLinkedQueue<Set<PlayerCharacter>> failedCharacters = new ConcurrentLinkedQueue<>();
    private SetVar<Region> webRegions;

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
        SC2MapDAO mapDAO,
        VarDAO varDAO,
        PostgreSQLUtils postgreSQLUtils,
        @Qualifier("dbExecutorService") ExecutorService dbExecutorService,
        Validator validator
    )
    {
        this.api = api;
        this.playerCharacterDAO = playerCharacterDAO;
        this.matchDAO = matchDAO;
        this.matchParticipantDAO = matchParticipantDAO;
        this.seasonDAO = seasonDAO;
        this.mapDAO = mapDAO;
        this.postgreSQLUtils = postgreSQLUtils;
        this.dbExecutorService = dbExecutorService;
        initVars(varDAO);
        validationPredicate = DAOUtils.beanValidationPredicate(validator);
    }

    private void initVars(VarDAO varDAO)
    {
        webRegions = new SetVar<>(varDAO, "match.web.regions",
            r->r == null ? null : String.valueOf(r.getId()),
            s->s == null || s.isEmpty() ? null : Region.from(Integer.parseInt(s)), false);
        //catch errors to allow autowiring in tests
        try
        {
            webRegions.load();
            if(!webRegions.getValue().isEmpty()) LOG.warn("Loaded web regions for match history: {}", webRegions.getValue());
        }
        catch (Exception ex)
        {
            LOG.error(ex.getMessage(), ex);
        }
    }

    public void addWebRegion(Region region)
    {
        if(webRegions.getValue().add(region)) webRegions.save();
    }

    public void removeWebRegion(Region region)
    {
        if(webRegions.getValue().remove(region)) webRegions.save();
    }

    public Set<Region> getWebRegions()
    {
        return Collections.unmodifiableSet(webRegions.getValue());
    }

    public void update(UpdateContext updateContext, Region... regions)
    {
        if(updateContext.getExternalUpdate() == null || updateContext.getInternalUpdate() == null) return;

        //Active players can't be updated retroactively, so there is no reason to sync with other services here
        int matchCount = saveMatches(updateContext.getInternalUpdate(), regions);
        matchDAO.removeExpired();
        LOG.info("Saved {} matches for {}", matchCount, regions);
    }

    private int saveMatches(Instant lastUpdated, Region... regions)
    {
        int r1 = saveFailedMatches();
        LOG.debug("Saved {} previously failed matches", r1);
        //clear here to avoid unbound retries of the same characters
        boolean web = !Collections.disjoint(webRegions.getValue(), List.of(regions));
        List<PlayerCharacter> characters = web
            ? playerCharacterDAO.findTopRecentlyActiveCharacters
                (
                    OffsetDateTime.ofInstant(lastUpdated, ZoneId.systemDefault()),
                    WEB_QUEUE_TYPE,
                    WEB_TEAM_TYPE,
                    List.of(regions),
                    WEB_CHARACTER_COUNT
                )
            : playerCharacterDAO
                .findRecentlyActiveCharacters(OffsetDateTime.ofInstant(lastUpdated, ZoneId.systemDefault()), regions);
        if(web) LOG.warn("Using web API for {} matches, top {} players of {} {}",
                regions, WEB_CHARACTER_COUNT, WEB_QUEUE_TYPE, WEB_TEAM_TYPE);
        return r1 + saveMatches(characters, true, web);
    }

    private int saveFailedMatches()
    {
        int i = 0;
        Set<PlayerCharacter> chars;
        while((chars = failedCharacters.poll()) != null)
        {
            if(chars.size() > FAILED_MATCHES_MAX)
            {
                LOG.debug("Dropped failed matches batch: {}/{}", chars.size(), FAILED_MATCHES_MAX);
            }
            else
            {
                LOG.debug("Retrying {} previously failed matches", chars.size());
                i += saveMatches(chars, false, false);
            }
        }
        return i;
    }

    private int saveMatches(Iterable<? extends PlayerCharacter> characters, boolean saveFailedCharacters, boolean web)
    {
        List<Future<?>> dbTasks = new ArrayList<>();
        AtomicInteger count = new AtomicInteger(0);
        Set<PlayerCharacter> errors = new HashSet<>();
        api.getMatches(characters, errors, web)
            .flatMap(m->Flux.fromArray(m.getT1().getMatches())
                .zipWith(Flux.fromStream(Stream.iterate(m.getT2(), i->m.getT2()))))
            .buffer(BATCH_SIZE)
            .doOnNext(b->count.getAndAdd(b.size()))
            .toStream()
            .forEach(m->dbTasks.add(dbExecutorService.submit(()->matchService.saveMatches(m))));
        MiscUtil.awaitAndLogExceptions(dbTasks, true);
        if(saveFailedCharacters) failedCharacters.add(errors);
        return count.get();
    }

    @Transactional
    protected void saveMatches(List<Tuple2<BlizzardMatch, PlayerCharacter>> matches)
    {
        matches = matches.stream()
            .filter(t->validationPredicate.test(t.getT1()))
            .collect(Collectors.toList());
        SC2Map[] mapBatch = new SC2Map[matches.size()];
        Match[] matchBatch = new Match[matches.size()];
        MatchParticipant[] participantBatch = new MatchParticipant[matches.size()];
        List<Tuple4<SC2Map, Match, BaseMatch.Decision, PlayerCharacter>> meta = new ArrayList<>();
        for(int i = 0; i < matches.size(); i++)
        {
            Tuple2<BlizzardMatch, PlayerCharacter> match = matches.get(i);
            SC2Map map = new SC2Map(null, match.getT1().getMap());
            Match localMatch = Match.of(match.getT1(), null, match.getT2().getRegion());
            mapBatch[i] = map;
            matchBatch[i] = localMatch;
            meta.add(Tuples.of(map, localMatch, match.getT1().getDecision(), match.getT2()));
        }
        Arrays.sort(mapBatch, SC2Map.NATURAL_ID_COMPARATOR);
        mapDAO.merge(mapBatch);
        meta.forEach(t->t.getT2().setMapId(t.getT1().getId()));
        Arrays.sort(matchBatch, Match.NATURAL_ID_COMPARATOR);
        matchDAO.merge(matchBatch);
        for(int i = 0; i < meta.size(); i++)
        {
            Tuple4<SC2Map, Match, BaseMatch.Decision, PlayerCharacter> participant = meta.get(i);
            participantBatch[i] = new MatchParticipant
            (
                participant.getT2().getId(),
                participant.getT4().getId(),
                participant.getT3()
            );
        }
        matchParticipantDAO.merge(participantBatch);
        LOG.debug("Saved {} matches", matches.size());
    }

    private void identify(UpdateContext updateContext)
    {
        postgreSQLUtils.vacuumAnalyze();
        int identified = matchParticipantDAO.identify(
            seasonDAO.getMaxBattlenetId(),
            /*
                Matches are fetched retroactively, some of them can happen before the lastUpdated instant.
                Try to catch these matches by moving the start instant back in time.
             */
            OffsetDateTime.ofInstant(updateContext.getExternalUpdate(), ZoneOffset.systemDefault()).minusMinutes(MatchParticipantDAO.IDENTIFICATION_FRAME_MINUTES)
        );
        LOG.info("Identified {} matches", identified);
    }

    private void calculateDuration(UpdateContext updateContext)
    {
        OffsetDateTime from = OffsetDateTime.ofInstant(updateContext.getExternalUpdate(), ZoneOffset.systemDefault())
            .minusMinutes(MatchParticipantDAO.IDENTIFICATION_FRAME_MINUTES);
        int updated = matchDAO.updateDuration(from);
        LOG.debug("Calculated duration of {} matches", updated);
    }

    public void updateMeta(UpdateContext updateContext)
    {
        identify(updateContext);
        calculateDuration(updateContext);
    }

}
