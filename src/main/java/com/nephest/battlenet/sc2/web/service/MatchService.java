// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardMatch;
import com.nephest.battlenet.sc2.model.local.CollectionVar;
import com.nephest.battlenet.sc2.model.local.Match;
import com.nephest.battlenet.sc2.model.local.MatchParticipant;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.SC2Map;
import com.nephest.battlenet.sc2.model.local.TimerVar;
import com.nephest.battlenet.sc2.model.local.Var;
import com.nephest.battlenet.sc2.model.local.dao.DAOUtils;
import com.nephest.battlenet.sc2.model.local.dao.MatchDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchParticipantDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.SC2MapDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.service.EventService;
import com.nephest.battlenet.sc2.util.MiscUtil;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.NonNull;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Validator;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;


@Service
public class MatchService
{

    private static final Logger LOG = LoggerFactory.getLogger(MatchService.class);
    public static final int BATCH_SIZE = 1000;
    public static final int FAILED_MATCHES_MAX = 100;
    public static final Duration MATCH_UPDATE_FRAME = Duration.ofMinutes(50);
    public static final String REQUEST_LIMIT_PRIORITY_NAME = "match";
    public static final Duration REQUEST_LIMIT_PRIORITY_OFFSET = Duration.ofMinutes(5);

    private final BlizzardSC2API api;
    private final MatchDAO matchDAO;
    private final MatchParticipantDAO matchParticipantDAO;
    private final PlayerCharacterDAO playerCharacterDAO;
    private final SeasonDAO seasonDAO;
    private final SC2MapDAO mapDAO;
    private final AlternativeLadderService alternativeLadderService;
    private final EventService eventService;
    private final UpdateService updateService;
    private final ExecutorService dbExecutorService;
    private final GlobalContext globalContext;
    private final Predicate<BlizzardMatch> validationPredicate;
    private final ConcurrentLinkedQueue<Set<PlayerCharacterNaturalId>> failedCharacters = new ConcurrentLinkedQueue<>();
    private CollectionVar<Set<Region>, Region> webRegions;
    private final Map<Region, Var<Set<PlayerCharacter>>> pendingCharacters = new EnumMap<>(Region.class);
    private TimerVar updateMatchesTask;
    private UpdateContext updateContext;

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
        AlternativeLadderService alternativeLadderService,
        UpdateService updateService,
        EventService eventService,
        @Qualifier("secondaryDbExecutorService") ExecutorService dbExecutorService,
        Validator validator,
        GlobalContext globalContext
    )
    {
        this.api = api;
        this.playerCharacterDAO = playerCharacterDAO;
        this.matchDAO = matchDAO;
        this.matchParticipantDAO = matchParticipantDAO;
        this.seasonDAO = seasonDAO;
        this.mapDAO = mapDAO;
        this.alternativeLadderService = alternativeLadderService;
        this.eventService = eventService;
        this.updateService = updateService;
        this.dbExecutorService = dbExecutorService;
        this.globalContext = globalContext;
        subToEvents(eventService);
        initVars(varDAO);
        validationPredicate = DAOUtils.beanValidationPredicate(validator);
    }

    private void initVars(VarDAO varDAO)
    {
        webRegions = WebServiceUtil.loadRegionSetVar(varDAO, "match.web.regions", "Loaded web regions for match history: {}");
        for(Region region : globalContext.getActiveRegions())
        {
            Var<Set<PlayerCharacter>> pendingCharsVar = new Var<>
            (
                varDAO, region.getId() + ".match.character.pending",
                chars->chars.isEmpty()
                    ? null
                    : chars.stream()
                        .map(PlayerCharacter::getId)
                        .map(String::valueOf)
                        .collect(Collectors.joining(",")),
                str->str == null
                    ? new HashSet<>()
                    : Flux.fromStream(Arrays.stream(str.split(",")).map(Long::valueOf))
                        .buffer(500)
                        .flatMapIterable(batch->playerCharacterDAO.find(batch.toArray(Long[]::new)))
                        .collect(Collectors.toSet())
                        .block(),
                false
            );
            pendingCharacters.put(region, pendingCharsVar);
            try
            {
                pendingCharsVar.load();
                LOG.debug
                (
                    "Loaded {} pending match characters, {}",
                    pendingCharsVar.getValue().size(),
                    region
                );
            }
            catch (RuntimeException e)
            {
                pendingCharsVar.setValue(new HashSet<>());
                LOG.error(e.toString(), e);
            }
        }
        updateMatchesTask = new TimerVar
        (
            varDAO,
            "match.updated",
            false,
            MATCH_UPDATE_FRAME,
            this::update,
            true
        );
        updateMatchesTask.tryLoad();
    }

    private void subToEvents(EventService eventService)
    {
        eventService.getLadderCharacterActivityEvent()
            .subscribe(character->pendingCharacters.get(character.getRegion()).getValue().add(character));
        eventService.getLadderUpdateEvent()
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(allStats->{
                pendingCharacters.values().forEach(Var::save);
                LOG.debug
                (
                    "Pending characters: {}",
                    pendingCharacters.values().stream()
                        .map(Var::getValue)
                        .mapToInt(Collection::size)
                        .sum()
                );
                UpdateContext uc = updateService.getUpdateContext(null);
                if(updateMatchesTask.runIfAvailable()) updateContext = uc;
            });
    }

    public Duration getMatchUpdateFrame()
    {
        return updateMatchesTask.getDurationBetweenRuns();
    }

    public void setMatchUpdateFrame(@NonNull Duration matchUpdateFrame)
    {
        updateMatchesTask.setDurationBetweenRuns(matchUpdateFrame);
        updateMatchesTask.save();
        LOG.info("Match update frame: {}", matchUpdateFrame);
    }

    public boolean isWeb(Region... regions)
    {
        boolean autoWeb = false;
        for(Region region : regions)
        {
            if(alternativeLadderService.isAutoWeb(region))
            {
                autoWeb = true;
                break;
            }
        }
        return autoWeb || !Collections.disjoint(webRegions.getValue(), List.of(regions));
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

    private void update()
    {
        UpdateContext uc = getUpdateContext();
        Map<Region, Set<PlayerCharacter>> pendingCharacters = copyAndClearPendingCharacters();
        setRequestLimitPriority(pendingCharacters);
        update(pendingCharacters, globalContext.getActiveRegions().toArray(new Region[0]));
        matchService.updateMeta(uc);
        eventService.createMatchUpdateEvent(new MatchUpdateContext(pendingCharacters, uc));
    }

    private void setRequestLimitPriority(Map<Region, Set<PlayerCharacter>> characters)
    {
        int limit = calculateRequestLimit(characters);
        api.addRequestLimitPriority(REQUEST_LIMIT_PRIORITY_NAME, limit);
        LOG.info("Using {} request limit priority", limit);
    }

    private int calculateRequestLimit(Map<Region, Set<PlayerCharacter>> characters)
    {
        int characterCount = characters.values().stream()
            .mapToInt(Collection::size)
            .sum();
        int limit = (int) Math.ceil
        (
            characterCount / (double) updateMatchesTask.getDurationBetweenRuns()
                .minus(REQUEST_LIMIT_PRIORITY_OFFSET)
                .toSeconds()
        );
        return Math.min(Math.max(limit, 1),
            api.getRequestsPerSecondCap(globalContext.getActiveRegions().iterator().next()) / 2);
    }

    private Map<Region, Set<PlayerCharacter>> copyAndClearPendingCharacters()
    {
        Map<Region, Set<PlayerCharacter>> copy = pendingCharacters.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e->e.getValue().getValue()));
        pendingCharacters.values().forEach(var->{
            var.getValue().clear();
            var.save();
        });
        return copy;
    }

    private UpdateContext getUpdateContext()
    {
        return updateContext != null
            ? updateContext
            : updateMatchesTask.getValue() != null
                ? new UpdateContext(updateMatchesTask.getValue(), updateMatchesTask.getValue())
                : new UpdateContext
                (
                    Instant.now().minus(updateMatchesTask.getDurationBetweenRuns()),
                    Instant.now().minus(updateMatchesTask.getDurationBetweenRuns())
                );
    }

    private void update(Map<Region, Set<PlayerCharacter>> pendingCharacters, Region... regions)
    {
        int matchCount = saveMatches(pendingCharacters, regions);
        matchDAO.removeExpired();
        LOG.info("Saved {} matches for {}", matchCount, regions);
        if(api.isAutoForceRegion() && matchCount < 1)
        {
            LOG.warn("No matches found in {} regions", (Object[]) regions);
            for(Region region : regions) api.setForceRegion(region);
        }
    }

    private int saveMatches(Map<Region, Set<PlayerCharacter>> pendingCharacters, Region... regions)
    {
        int r1 = saveFailedMatches();
        LOG.debug("Saved {} previously failed matches", r1);
        //clear here to avoid unbound retries of the same characters
        boolean web = isWeb(regions);
        List<PlayerCharacter> characters = Arrays.stream(regions)
            .distinct()
            .map(pendingCharacters::get)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
        if(web) LOG.warn("Using web API for {} matches {} players", regions, characters.size());
        return r1 + saveMatches(characters, true, web);
    }

    private int saveFailedMatches()
    {
        int i = 0;
        Set<PlayerCharacterNaturalId> chars;
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

    private int saveMatches(Iterable<? extends PlayerCharacterNaturalId> characters, boolean saveFailedCharacters, boolean web)
    {
        List<Future<?>> dbTasks = new ArrayList<>();
        AtomicInteger count = new AtomicInteger(0);
        Set<PlayerCharacterNaturalId> errors = new HashSet<>();
        api.getMatches(characters, errors, web, REQUEST_LIMIT_PRIORITY_NAME)
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

    //This method fails in a rare occasion due to unknown reason. Retry for now, should be properly fixed later.
    @Transactional @Retryable
    protected void saveMatches(List<Tuple2<BlizzardMatch, PlayerCharacterNaturalId>> matches)
    {
        matches = matches.stream()
            .filter(t->validationPredicate.test(t.getT1()))
            .collect(Collectors.toList());
        SC2Map[] mapBatch = new SC2Map[matches.size()];
        Match[] matchBatch = new Match[matches.size()];
        MatchParticipant[] participantBatch = new MatchParticipant[matches.size()];
        List<Tuple4<SC2Map, Match, BaseMatch.Decision, PlayerCharacterNaturalId>> meta = new ArrayList<>();
        for(int i = 0; i < matches.size(); i++)
        {
            Tuple2<BlizzardMatch, PlayerCharacterNaturalId> match = matches.get(i);
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
            Tuple4<SC2Map, Match, BaseMatch.Decision, PlayerCharacterNaturalId> participant = meta.get(i);
            participantBatch[i] = new MatchParticipant
            (
                participant.getT2().getId(),
                ((PlayerCharacter) participant.getT4()).getId(),
                participant.getT3()
            );
        }
        matchParticipantDAO.merge(participantBatch);
        LOG.debug("Saved {} matches", matches.size());
    }

    private void identify(UpdateContext updateContext)
    {
        for(Integer season : seasonDAO.getLastInAllRegions())
        {
            int identified = matchParticipantDAO.identify
            (
                season,
                calculateRetroactiveDateTime(updateContext)
            );
            LOG.info("Identified {} matches", identified);
        }
    }

    private void calculateRatingDifference(UpdateContext updateContext)
    {
        int count = matchParticipantDAO
            .calculateRatingDifference(calculateRetroactiveDateTime(updateContext));
        LOG.info("Calculated rating difference of {} match participants", count);
    }

    private void calculateDuration(UpdateContext updateContext)
    {
        int updated = matchDAO.updateDuration(calculateRetroactiveDateTime(updateContext));
        LOG.debug("Calculated duration of {} matches", updated);
    }

    /*
        Matches are fetched retroactively, some of them can happen before the lastUpdated instant.
        Try to catch these matches by moving the start instant back in time.
     */
    private static OffsetDateTime calculateRetroactiveDateTime(UpdateContext updateContext)
    {
        return OffsetDateTime.ofInstant(updateContext.getExternalUpdate(), ZoneOffset.systemDefault())
            .minusMinutes(MatchParticipantDAO.IDENTIFICATION_FRAME_MINUTES);
    }

    public void updateMeta(UpdateContext updateContext)
    {
        identify(updateContext);
        calculateRatingDifference(updateContext);
        calculateDuration(updateContext);
    }

}
