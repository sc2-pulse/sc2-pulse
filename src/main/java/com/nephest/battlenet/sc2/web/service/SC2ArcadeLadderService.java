// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.BasePlayerCharacter;
import com.nephest.battlenet.sc2.model.BaseTeam;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.CollectionVar;
import com.nephest.battlenet.sc2.model.local.Division;
import com.nephest.battlenet.sc2.model.local.InstantVar;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.LeagueTier;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.Team;
import com.nephest.battlenet.sc2.model.local.TeamMember;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.DAOUtils;
import com.nephest.battlenet.sc2.model.local.dao.DivisionDAO;
import com.nephest.battlenet.sc2.model.local.dao.FastTeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.LeagueDAO;
import com.nephest.battlenet.sc2.model.local.dao.LeagueTierDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.TeamMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.local.inner.AlternativeTeamData;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyId;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyIdEntry;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import com.nephest.battlenet.sc2.model.sc2arcade.SC2ArcadeLadderDivision;
import com.nephest.battlenet.sc2.model.sc2arcade.SC2ArcadeLadderQueuePair;
import com.nephest.battlenet.sc2.model.sc2arcade.SC2ArcadeLadderRoster;
import com.nephest.battlenet.sc2.model.sc2arcade.SC2ArcadeLadderRosterTeam;
import com.nephest.battlenet.sc2.model.sc2arcade.SC2ArcadeLadderSnapshot;
import com.nephest.battlenet.sc2.model.sc2arcade.SC2ArcadeLadderTierThreshold;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.convert.ConversionService;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Validator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * <p>
 * Ladder crawler on top of {@link SC2ArcadeLadderAPI}. It is the source of
 * truth for the regions that were enabled via {@link #addRegion(Region)}.
 * </p>
 * <p>
 * The API has no per-member races, so only one-member teams are supported.
 * There are no clans, so never save clans here, because a null clan means that
 * a player has left their clan.
 * </p>
 */
@Service
public class SC2ArcadeLadderService
{

    private static final Logger LOG =
        LoggerFactory.getLogger(SC2ArcadeLadderService.class);

    public static final List<SC2ArcadeLadderQueuePair> QUEUE_PAIRS = List.of
    (
        new SC2ArcadeLadderQueuePair(QueueType.LOTV_1V1, TeamType.ARRANGED),
        new SC2ArcadeLadderQueuePair(QueueType.LOTV_2V2, TeamType.RANDOM),
        new SC2ArcadeLadderQueuePair(QueueType.LOTV_3V3, TeamType.RANDOM),
        new SC2ArcadeLadderQueuePair(QueueType.LOTV_4V4, TeamType.RANDOM)
    );
    /**
     * These regions have no other ladder source, so they can't be removed.
     */
    public static final Set<Region> REQUIRED_REGIONS = Set.of(Region.CN);
    public static final int LADDER_BATCH_SIZE = StatsService.LADDER_BATCH_SIZE;
    public static final int REQUEST_CONCURRENCY = 4;
    public static final int MEMBER_LOOKUPS_PER_SCAN = 100;
    public static final Duration MEMBER_REFRESH_FRAME = Duration.ofHours(24);

    private record DivisionInfo
    (
        long ladderId,
        BaseLeague league,
        BaseLeagueTier.LeagueTierType tierType
    )
    {}

    private record Ladder(DivisionInfo division, SC2ArcadeLadderRoster roster){}

    private record LadderTeam
    (
        SC2ArcadeLadderSnapshot snapshot,
        SC2ArcadeLadderRosterTeam team,
        @Nullable Race race,
        PlayerCharacterNaturalId character,
        @Nullable String name,
        TeamLegacyId legacyId
    )
    {}

    /**
     * Validated data that is ready to be saved.
     */
    private record LadderData(DivisionInfo division, List<LadderTeam> teams){}

    private static final Comparator<LadderTeam> RELEVANCE_COMPARATOR =
        Comparator.<LadderTeam, OffsetDateTime>comparing(
            t->t.team().joinedAt())
            .thenComparing
            (
                t->t.snapshot().lastCheckedAt(),
                Comparator.nullsFirst(Comparator.naturalOrder())
            );

    private final SC2ArcadeLadderAPI api;
    private final LeagueDAO leagueDAO;
    private final LeagueTierDAO leagueTierDAO;
    private final DivisionDAO divisionDAO;
    private final FastTeamDAO fastTeamDAO;
    private final TeamDAO teamDAO;
    private final AccountDAO accountDAO;
    private final PlayerCharacterDAO playerCharacterDAO;
    private final TeamMemberDAO teamMemberDAO;
    private final ConversionService conversionService;
    private final ExecutorService dbExecutorService;
    private final Predicate<SC2ArcadeLadderRosterTeam>
        rosterTeamValidationPredicate;
    private final Predicate<BaseTeam> teamValidationPredicate;
    private final SC2ArcadeLadderService sc2ArcadeLadderService;

    private final CollectionVar<Set<Region>, Region> regions;
    private final Map<Region, InstantVar> memberRefreshInstants =
        new EnumMap<>(Region.class);
    private final Map<Region, SC2ArcadeLadderMemberCache> memberCaches =
        new ConcurrentHashMap<>();
    private final Set<String> unknownRaces = ConcurrentHashMap.newKeySet();
    private final PendingLadderData pendingLadderData = new PendingLadderData();

    @Autowired
    public SC2ArcadeLadderService
    (
        SC2ArcadeLadderAPI api,
        LeagueDAO leagueDAO,
        LeagueTierDAO leagueTierDAO,
        DivisionDAO divisionDAO,
        FastTeamDAO fastTeamDAO,
        TeamDAO teamDAO,
        AccountDAO accountDAO,
        PlayerCharacterDAO playerCharacterDAO,
        TeamMemberDAO teamMemberDAO,
        VarDAO varDAO,
        @Qualifier("sc2StatsConversionService")
        ConversionService conversionService,
        Validator validator,
        @Qualifier("dbExecutorService") ExecutorService dbExecutorService,
        @Lazy SC2ArcadeLadderService sc2ArcadeLadderService
    )
    {
        this.api = api;
        this.leagueDAO = leagueDAO;
        this.leagueTierDAO = leagueTierDAO;
        this.divisionDAO = divisionDAO;
        this.fastTeamDAO = fastTeamDAO;
        this.teamDAO = teamDAO;
        this.accountDAO = accountDAO;
        this.playerCharacterDAO = playerCharacterDAO;
        this.teamMemberDAO = teamMemberDAO;
        this.conversionService = conversionService;
        this.dbExecutorService = dbExecutorService;
        this.rosterTeamValidationPredicate =
            DAOUtils.beanValidationPredicate(validator);
        this.teamValidationPredicate =
            DAOUtils.beanValidationPredicate(validator);
        this.sc2ArcadeLadderService = sc2ArcadeLadderService;
        this.regions = WebServiceUtil.loadRegionSetVar
        (
            varDAO,
            "ladder.sc2arcade.regions",
            "Loaded regions for SC2Arcade ladder: {}"
        );
        regions.getValue().addAll(REQUIRED_REGIONS);
        for(Region region : Region.values())
        {
            InstantVar refreshInstant = new InstantVar
            (
                varDAO,
                region.getId() + ".ladder.sc2arcade.members.refreshed",
                false
            );
            refreshInstant.tryLoad();
            memberRefreshInstants.put(region, refreshInstant);
        }
    }

    public boolean isArcadeRegion(Region region)
    {
        return regions.getValue().contains(region);
    }

    public Set<Region> getRegions()
    {
        return Set.copyOf(regions.getValue());
    }

    public void addRegion(Region region)
    {
        if(regions.getValue().add(region)) regions.save();
    }

    public void removeRegion(Region region)
    {
        if(REQUIRED_REGIONS.contains(region))
            throw new IllegalArgumentException(
                "Required region can't be removed: " + region);

        if(regions.getValue().remove(region)) regions.save();
    }

    protected InstantVar getMemberRefreshInstant(Region region)
    {
        return memberRefreshInstants.get(region);
    }

    protected SC2ArcadeLadderMemberCache getMemberCache(Region region)
    {
        return memberCaches.get(region);
    }

    protected void clearMemberCache()
    {
        memberCaches.clear();
    }

    protected PendingLadderData copyAndClearPendingData()
    {
        PendingLadderData pending =
            PendingLadderData.immutableCopy(pendingLadderData);
        pendingLadderData.clear();
        return pending;
    }

    /**
     * @param region region
     * @param pulseSeasonId pulse season id
     * @return characters with fresh names, without ids. Empty if there is no
     * data for this season.
     */
    public Set<PlayerCharacter> getCachedCharacters
    (
        Region region,
        int pulseSeasonId
    )
    {
        SC2ArcadeLadderMemberCache cache = memberCaches.get(region);
        return cache == null || cache.getPulseSeasonId() != pulseSeasonId
            ? Set.of()
            : cache.getCharacters();
    }

    /**
     * Unknown characters and characters without resolved names are skipped, as
     * well as failed requests.
     *
     * @param region region
     * @param characters characters of the region
     * @return characters with current names, without ids
     */
    public Flux<PlayerCharacter> getCharacters
    (
        Region region,
        Iterable<? extends PlayerCharacterNaturalId> characters
    )
    {
        return Flux.<PlayerCharacterNaturalId>fromIterable(characters)
            .buffer(SC2ArcadeLadderAPI.CHARACTER_BATCH_SIZE)
            .flatMap
            (
                batch->WebServiceUtil.getOnErrorLogAndSkipMono(
                    api.getCharacters(region, batch)),
                REQUEST_CONCURRENCY
            )
            .flatMapIterable(Function.identity())
            .flatMap(character->Mono.justOrEmpty(character.naturalId(region)
                .flatMap(id->character.resolvedName()
                    .map(name->PlayerCharacter.of(new Account(), id, name)))));
    }

    /*
        Log and skip errors, but never ignore access errors, because the whole
        region must be aborted in such cases.
     */
    private static <T> Mono<T> logAndSkip(Mono<T> mono)
    {
        return mono.onErrorResume
        (
            t->!SC2ArcadeLadderAPI.isAccessDenied(t),
            t->WebServiceUtil.getOnErrorLogAndSkipMono(Mono.error(t))
        );
    }

    /**
     * Verify that the API is accessible and log its data freshness. There is
     * no freshness limit because old data can't overwrite new data.
     *
     * @param region region
     * @throws NoRetryException if the API is not accessible
     */
    public void checkCoverage(Region region)
    {
        logAndSkip(api.getCoverage(region))
            .blockOptional()
            .ifPresent(coverage->LOG.info
            (
                "SC2Arcade ladder coverage: {} {}",
                region, coverage.coverage()
            ));
    }

    /**
     * The last known season is returned if it has ended and there is no new
     * season yet.
     *
     * @param region region
     * @return current or last season, not persisted
     */
    public Season getCurrentSeason(Region region)
    {
        return api.getCurrentSeason(region).block().toSeason(region);
    }

    public Map<SC2ArcadeLadderQueuePair, List<SC2ArcadeLadderTierThreshold>>
    getTierThresholds(Season season)
    {
        Map<SC2ArcadeLadderQueuePair, List<SC2ArcadeLadderTierThreshold>>
            result = new HashMap<>();
        for(SC2ArcadeLadderQueuePair pair : QUEUE_PAIRS)
            logAndSkip(api.getTierThresholds(
                season.getRegion(), season.getBattlenetId(), pair))
                .blockOptional()
                .ifPresent(thresholds->result.put(pair, thresholds));
        return result;
    }

    /**
     * Tiers without thresholds are not changed. Merges are cached, so new
     * thresholds can be saved with a delay.
     *
     * @param season persisted season
     * @param thresholds thresholds
     */
    public void saveTierThresholds
    (
        Season season,
        Map<SC2ArcadeLadderQueuePair, List<SC2ArcadeLadderTierThreshold>>
            thresholds
    )
    {
        thresholds.forEach((pair, pairThresholds)->{
            for(SC2ArcadeLadderTierThreshold threshold : pairThresholds)
            {
                BaseLeague.LeagueType leagueType =
                    parseLeague(threshold.league());
                BaseLeagueTier.LeagueTierType tierType =
                    parseTier(threshold.tier());
                if
                (
                    leagueType == null
                    || tierType == null
                    || threshold.minRating() == null
                    || threshold.maxRating() == null
                ) continue;

                League league = leagueDAO.merge(new League
                (
                    null,
                    season.getId(),
                    leagueType,
                    pair.queueType(),
                    pair.teamType()
                ));
                leagueTierDAO.merge(new LeagueTier
                (
                    null,
                    league.getId(),
                    tierType,
                    threshold.minRating(),
                    threshold.maxRating()
                ));
            }
        });
    }

    /**
     * Leagues and tiers must exist, {@link FastTeamDAO} must be loaded.
     *
     * @param season persisted season
     * @param data ladders to update. Unsupported ladders are ignored.
     * @return DB tasks
     * @throws NoRetryException if the API is not accessible
     */
    public List<Future<Void>> updateSeason
    (
        Season season,
        Map<QueueType, Set<BaseLeague.LeagueType>> data
    )
    {
        saveTierThresholds(season, getTierThresholds(season));
        Set<SC2ArcadeLadderQueuePair> advertisedPairs =
            getAdvertisedPairs(season);
        SC2ArcadeLadderMemberCache cache = getOrCreateCache(season);
        Set<SC2ArcadeLadderQueuePair> readRankings = new HashSet<>();
        if(mustRefreshMembers(season.getRegion(), cache))
        {
            refreshMembers(season, cache, advertisedPairs);
            readRankings.addAll(advertisedPairs);
        }

        List<Future<Void>> tasks = new ArrayList<>();
        int lookups = MEMBER_LOOKUPS_PER_SCAN;
        for(SC2ArcadeLadderQueuePair pair : QUEUE_PAIRS)
        {
            Set<BaseLeague.LeagueType> leagues =
                data.getOrDefault(pair.queueType(), Set.of());
            if(leagues.isEmpty() || !advertisedPairs.contains(pair)) continue;

            List<SC2ArcadeLadderDivision> divisions = logAndSkip(
                api.getDivisions(
                    season.getRegion(), season.getBattlenetId(), pair))
                .blockOptional()
                .orElse(List.of());
            List<Ladder> ladders = getLadders(season, pair, leagues, divisions);
            lookups -= findMissingMembers
            (
                season,
                pair,
                cache,
                divisions,
                ladders,
                !readRankings.contains(pair),
                lookups
            );
            List<LadderData> ladderData = transform(pair, cache, ladders);
            for(int i = 0; i < ladderData.size(); i += LADDER_BATCH_SIZE)
            {
                List<LadderData> batch = List.copyOf(ladderData.subList(
                    i, Math.min(i + LADDER_BATCH_SIZE, ladderData.size())));
                tasks.add(dbExecutorService.submit
                (
                    ()->sc2ArcadeLadderService.saveLadders(season, batch),
                    null
                ));
            }
        }
        checkSeasonChange(season);
        return tasks;
    }

    private void checkSeasonChange(Season season)
    {
        WebServiceUtil.getOnErrorLogAndSkipMono(
            api.getCurrentSeason(season.getRegion()))
            .blockOptional()
            .filter(s->!Objects.equals(s.season(), season.getBattlenetId()))
            .ifPresent(s->LOG.warn("Season changed during the scan: {} -> {}",
                season, s.season()));
    }

    private Set<SC2ArcadeLadderQueuePair> getAdvertisedPairs(Season season)
    {
        return api.getQueues(season.getRegion(), season.getBattlenetId())
            .block()
            .stream()
            .flatMap(queue->QUEUE_PAIRS.stream()
                .filter(pair->Objects.equals(
                        pair.queueType().getId(), queue.queueId())
                    && pair.teamTypeAlias().equalsIgnoreCase(queue.teamType())))
            .collect(Collectors.toSet());
    }

    private SC2ArcadeLadderMemberCache getOrCreateCache(Season season)
    {
        return memberCaches.compute(season.getRegion(), (region, cache)->
            cache == null
                || !cache.getPulseSeasonId().equals(season.getPulseSeasonId())
                    ? new SC2ArcadeLadderMemberCache(
                        region, season.getPulseSeasonId())
                    : cache);
    }

    private boolean mustRefreshMembers
    (
        Region region,
        SC2ArcadeLadderMemberCache cache
    )
    {
        Instant refreshed = memberRefreshInstants.get(region).getValue();
        return !cache.isInitialized()
            || refreshed == null
            || !refreshed.plus(MEMBER_REFRESH_FRAME)
                .isAfter(SC2Pulse.instant());
    }

    /*
        Members of a team can be found in any queue because team ids are shared
        between queues, so always read all queues.
     */
    private void refreshMembers
    (
        Season season,
        SC2ArcadeLadderMemberCache cache,
        Set<SC2ArcadeLadderQueuePair> pairs
    )
    {
        boolean complete = true;
        for(SC2ArcadeLadderQueuePair pair : pairs)
            complete = readRankings(season, pair, cache) && complete;
        if(!complete) return;

        cache.setInitialized();
        memberRefreshInstants.get(season.getRegion())
            .setValueAndSave(SC2Pulse.instant());
        LOG.info("Refreshed SC2Arcade ladder members: {}", season);
    }

    private boolean readRankings
    (
        Season season,
        SC2ArcadeLadderQueuePair pair,
        SC2ArcadeLadderMemberCache cache
    )
    {
        return logAndSkip(api
            .getRankings(season.getRegion(), season.getBattlenetId(), pair)
            .doOnNext(page->page.items().stream()
                .filter(ranking->ranking.teamId() != null)
                .forEach(ranking->cache.put(
                    ranking.teamId(), ranking.members())))
            .then(Mono.just(true))
            //the cursor has expired
            .retryWhen(Retry.max(1)
                .filter(SC2ArcadeLadderAPI::isGone)))
            .blockOptional()
            .orElse(false);
    }

    private List<Ladder> getLadders
    (
        Season season,
        SC2ArcadeLadderQueuePair pair,
        Set<BaseLeague.LeagueType> leagues,
        List<SC2ArcadeLadderDivision> divisions
    )
    {
        List<DivisionInfo> divisionInfos = divisions.stream()
            .flatMap(division->parseDivision(pair, division).stream())
            .filter(division->leagues.contains(division.league().getType()))
            .sorted(Comparator.comparing(DivisionInfo::ladderId))
            .toList();
        return Flux.fromIterable(divisionInfos)
            .flatMapSequential
            (
                division->logAndSkip(api.getRoster
                (
                    season.getRegion(),
                    season.getBattlenetId(),
                    division.ladderId()
                ))
                    .filter(roster->isSameSeason(season, division, roster))
                    .map(roster->new Ladder(division, roster)),
                REQUEST_CONCURRENCY
            )
            .collectList()
            .block();
    }

    private static boolean isSameSeason
    (
        Season season,
        DivisionInfo division,
        SC2ArcadeLadderRoster roster
    )
    {
        boolean result = roster.snapshot() != null
            && Objects.equals(
                roster.snapshot().season(), season.getBattlenetId());
        if(!result) LOG.warn("Skipped ladder {}, expected {}, snapshot: {}",
            division.ladderId(), season, roster.snapshot());
        return result;
    }

    private static Optional<DivisionInfo> parseDivision
    (
        SC2ArcadeLadderQueuePair pair,
        SC2ArcadeLadderDivision division
    )
    {
        BaseLeague.LeagueType league = parseLeague(division.league());
        BaseLeagueTier.LeagueTierType tier =
            league == BaseLeague.LeagueType.GRANDMASTER
                ? BaseLeagueTier.LeagueTierType.FIRST
                : parseTier(division.tier());
        if
        (
            division.ladderId() == null
            || league == null
            || tier == null
            || !BlizzardSC2API.isValidCombination(
                league, pair.queueType(), pair.teamType())
        )
        {
            LOG.warn("Skipped invalid division: {} {}", pair, division);
            return Optional.empty();
        }
        return Optional.of(new DivisionInfo
        (
            division.ladderId(),
            new BaseLeague(league, pair.queueType(), pair.teamType()),
            tier
        ));
    }

    @Nullable
    private static BaseLeague.LeagueType parseLeague(@Nullable String league)
    {
        return league == null
            ? null
            : BaseLeague.LeagueType.optionalFrom(league).orElse(null);
    }

    @Nullable
    private static BaseLeagueTier.LeagueTierType parseTier
    (@Nullable Integer tier)
    {
        return Arrays.stream(BaseLeagueTier.LeagueTierType.values())
            .filter(type->Objects.equals(type.getId(), tier))
            .findAny()
            .orElse(null);
    }

    @Nullable
    private Race parseRace(@Nullable String race)
    {
        if(race == null) return null;

        Race result = Race.optionalFrom(race).orElse(null);
        if(result == null && unknownRaces.add(race))
            LOG.warn("Unknown race: {}", race);
        return result;
    }

    private int findMissingMembers
    (
        Season season,
        SC2ArcadeLadderQueuePair pair,
        SC2ArcadeLadderMemberCache cache,
        List<SC2ArcadeLadderDivision> divisions,
        List<Ladder> ladders,
        boolean canReadRankings,
        int maxLookups
    )
    {
        Set<String> missing = ladders.stream()
            .flatMap(ladder->ladder.roster().items().stream())
            .filter(this::isValidTeam)
            .map(SC2ArcadeLadderRosterTeam::teamId)
            .filter(teamId->cache.getMember(teamId).isEmpty())
            .filter(cache::isLookupAvailable)
            .collect(Collectors.toSet());
        if(missing.isEmpty()) return 0;

        int storedTeams = divisions.stream()
            .map(SC2ArcadeLadderDivision::reportedMembers)
            .filter(Objects::nonNull)
            .mapToInt(Integer::intValue)
            .sum();
        int rankingPages = Math.max(1, (int) Math.ceil(
            storedTeams / (double) SC2ArcadeLadderAPI.PAGE_SIZE));
        if(canReadRankings && rankingPages < missing.size())
        {
            readRankings(season, pair, cache);
            missing.removeIf(teamId->cache.getMember(teamId).isPresent());
        }

        List<String> lookups = missing.stream().limit(maxLookups).toList();
        Flux.fromIterable(lookups)
            .flatMap
            (
                teamId->logAndSkip(api.getTeamMembers(
                    season.getRegion(), season.getBattlenetId(), teamId))
                    .doOnNext(members->cache.put(teamId, members)),
                REQUEST_CONCURRENCY
            )
            .blockLast();
        lookups.stream()
            .filter(teamId->cache.getMember(teamId).isEmpty())
            .forEach(cache::lookupFailed);
        LOG.debug("Missing members: {} {}, missing {}, looked up {}",
            season, pair, missing.size(), lookups.size());
        return lookups.size();
    }

    private boolean isValidTeam(SC2ArcadeLadderRosterTeam team)
    {
        //same constraints as in other ladder services
        return rosterTeamValidationPredicate.test(team)
            && teamValidationPredicate.test(team.toBaseTeam())
            && (team.wins() > 0 || team.losses() > 0)
            //Blizzard has no rating for such teams
            && team.mmr() >= 0;
    }

    private List<LadderData> transform
    (
        SC2ArcadeLadderQueuePair pair,
        SC2ArcadeLadderMemberCache cache,
        List<Ladder> ladders
    )
    {
        record Entry(long ladderId, LadderTeam team){}

        /*
            A team can be in several rosters if it was moved, and the old roster
            is not rechecked yet.
         */
        Map<TeamLegacyId, Entry> entries = ladders.stream()
            .flatMap(ladder->ladder.roster().items().stream()
                .filter(this::isValidTeam)
                .flatMap(team->transform(
                    pair, cache, ladder.roster().snapshot(), team).stream())
                .map(team->new Entry(ladder.division().ladderId(), team)))
            .collect(Collectors.toMap
            (
                entry->entry.team().legacyId(),
                Function.identity(),
                (l, r)->RELEVANCE_COMPARATOR.compare(l.team(), r.team()) >= 0
                    ? l
                    : r,
                LinkedHashMap::new
            ));
        Map<Long, List<LadderTeam>> teams = entries.values().stream()
            .collect(Collectors.groupingBy
            (
                Entry::ladderId,
                Collectors.mapping(Entry::team, Collectors.toList())
            ));
        return ladders.stream()
            .map(ladder->new LadderData
            (
                ladder.division(),
                teams.getOrDefault(ladder.division().ladderId(), List.of())
            ))
            .toList();
    }

    private Optional<LadderTeam> transform
    (
        SC2ArcadeLadderQueuePair pair,
        SC2ArcadeLadderMemberCache cache,
        SC2ArcadeLadderSnapshot snapshot,
        SC2ArcadeLadderRosterTeam team
    )
    {
        PlayerCharacterNaturalId character =
            cache.getMember(team.teamId()).orElse(null);
        if(character == null) return Optional.empty();

        Race race = parseRace(team.race());
        boolean is1v1 = pair.queueType() == QueueType.LOTV_1V1;
        if(is1v1 && race == null) return Optional.empty();

        TeamLegacyId legacyId = TeamLegacyId.standard(List.of(
            new TeamLegacyIdEntry
            (
                character.getRealm(),
                character.getBattlenetId(),
                is1v1 ? race : null
            )));
        return Optional.of(new LadderTeam
        (
            snapshot,
            team,
            race,
            character,
            cache.getName(character).orElse(null),
            legacyId
        ));
    }

    @Transactional
    public void saveLadders(Season season, List<LadderData> ladders)
    {
        for(LadderData ladder : ladders) saveLadder(season, ladder);
    }

    private void saveLadder(Season season, LadderData ladder)
    {
        Division division = saveDivision(season, ladder.division());
        Map<Team, LadderTeam> teams = ladder.teams().stream()
            .collect(Collectors.toMap(
                team->createTeam(season, ladder.division(), division, team),
                Function.identity()));
        Set<TeamLegacyUid> existingUids = findExistingTeamsWithoutRace(teams);
        Set<Team> changedTeams =
            teamDAO.merge(fastTeamDAO.merge(new HashSet<>(teams.keySet())));

        List<AlternativeTeamData> newCharacters = new ArrayList<>();
        Set<PlayerCharacter> existingCharacters = new HashSet<>();
        Set<PlayerCharacter> renamedCharacters = new HashSet<>();
        Set<TeamMember> members = new HashSet<>();
        teams.forEach((team, ladderTeam)->{
            if(team.getId() == null) return;

            pendingLadderData.getTeams().add(team.getId());
            PlayerCharacter character = playerCharacterDAO.find
            (
                season.getRegion(),
                ladderTeam.character().getRealm(),
                ladderTeam.character().getBattlenetId()
            )
                .orElse(null);
            if(character == null)
            {
                newCharacters.add(createCharacter(team, ladderTeam));
                return;
            }

            existingCharacters.add(character);
            if(ladderTeam.name() != null)
            {
                character.setName(ladderTeam.name());
                renamedCharacters.add(character);
            }
            //null races would erase existing races
            if
            (
                ladderTeam.race() != null
                || !existingUids.contains(TeamLegacyUid.of(team))
            ) members.add(createMember(team, character, ladderTeam.race()));
        });
        saveNewCharacters(newCharacters, members);
        renamedCharacters.stream()
            .sorted(PlayerCharacter.NATURAL_ID_COMPARATOR)
            .forEach(playerCharacterDAO::merge);
        teamMemberDAO.merge(members);
        pendingLadderData.getCharacters().addAll(existingCharacters);
        newCharacters.stream()
            .map(AlternativeTeamData::getCharacter)
            .forEach(pendingLadderData.getCharacters()::add);
        LOG.debug
        (
            "Ladder saved: {} {}({}/{} teams)",
            season, ladder.division(), changedTeams.size(), teams.size()
        );
    }

    private Set<TeamLegacyUid> findExistingTeamsWithoutRace
    (Map<Team, LadderTeam> teams)
    {
        return teams.entrySet().stream()
            .filter(e->e.getValue().race() == null)
            .map(Map.Entry::getKey)
            .filter(team->!teamDAO.findIdsByLegacyUids
            (
                Set.of(TeamLegacyUid.of(team)),
                team.getSeason(),
                team.getSeason() + 1
            ).isEmpty())
            .map(TeamLegacyUid::of)
            .collect(Collectors.toSet());
    }

    private Division saveDivision(Season season, DivisionInfo info)
    {
        BaseLeague league = info.league();
        LeagueTier tier = leagueTierDAO.findByLadder
        (
            season.getPulseSeasonId(),
            season.getRegion(),
            league.getType(),
            league.getQueueType(),
            league.getTeamType(),
            info.tierType()
        )
            .orElseThrow();
        //the division could be created without a tier by an alternative update
        Division division = divisionDAO.findDivision
        (
            season.getPulseSeasonId(),
            season.getRegion(),
            league.getQueueType(),
            league.getTeamType(),
            info.ladderId()
        )
            .orElseGet(()->new Division(null, tier.getId(), info.ladderId()));
        division.setTierId(tier.getId());
        return division.getId() != null
            ? divisionDAO.mergeById(division)
            : divisionDAO.merge(division);
    }

    private static Team createTeam
    (
        Season season,
        DivisionInfo info,
        Division division,
        LadderTeam ladderTeam
    )
    {
        SC2ArcadeLadderRosterTeam team = ladderTeam.team();
        BaseTeam baseTeam = team.toBaseTeam();
        OffsetDateTime now = SC2Pulse.offsetDateTime();
        OffsetDateTime observed =
            toLocalOffset(ladderTeam.snapshot().observedAt(), now);
        OffsetDateTime checked =
            toLocalOffset(ladderTeam.snapshot().lastCheckedAt(), observed);
        return new Team
        (
            null,
            season.getPulseSeasonId(),
            season.getRegion(),
            info.league(),
            info.tierType(),
            ladderTeam.legacyId(),
            division.getId(),
            baseTeam.getRating(),
            baseTeam.getWins(), baseTeam.getLosses(), baseTeam.getTies(),
            baseTeam.getPoints(),
            observed,
            toLocalOffset(team.joinedAt(), now),
            checked
        );
    }

    private static OffsetDateTime toLocalOffset
    (
        @Nullable OffsetDateTime odt,
        OffsetDateTime defaultValue
    )
    {
        return odt == null
            ? defaultValue
            : SC2Pulse.offsetDateTime(odt.withOffsetSameInstant(
                SC2Pulse.offsetDateTime().getOffset()));
    }

    private static TeamMember createMember
    (
        Team team,
        PlayerCharacter character,
        @Nullable Race race
    )
    {
        TeamMember member = new TeamMember
        (
            team.getId(),
            character.getId(),
            null, null, null, null
        );
        if(race != null)
            member.setGamesPlayed(race, team.getWins() + team.getLosses());
        return member;
    }

    //there are no accounts, use a fake one until a real one is found
    private AlternativeTeamData createCharacter
    (
        Team team,
        LadderTeam ladderTeam
    )
    {
        PlayerCharacterNaturalId id = ladderTeam.character();
        Region region = id.getRegion();
        String fakeBattleTag = BasePlayerCharacter.DEFAULT_FAKE_NAME + "#"
            + conversionService.convert(region, Integer.class)
            + id.getRealm()
            + id.getBattlenetId();
        Account account =
            new Account(null, Partition.of(region), fakeBattleTag);
        PlayerCharacter character =
            PlayerCharacter.of(account, id, ladderTeam.name());
        return new AlternativeTeamData
        (
            account,
            character,
            team,
            ladderTeam.race()
        );
    }

    //this ensures the consistent order for concurrent entities
    private void saveNewCharacters
    (
        List<AlternativeTeamData> newCharacters,
        Collection<TeamMember> members
    )
    {
        newCharacters.sort(Comparator.comparing(
            AlternativeTeamData::getAccount, Account.NATURAL_ID_COMPARATOR));
        for(AlternativeTeamData data : newCharacters)
            accountDAO.merge(data.getAccount(), data.getCharacter());

        newCharacters.sort(Comparator.comparing
        (
            AlternativeTeamData::getCharacter,
            PlayerCharacter.NATURAL_ID_COMPARATOR
        ));
        for(AlternativeTeamData data : newCharacters)
        {
            data.getCharacter().setAccountId(data.getAccount().getId());
            PlayerCharacter character =
                playerCharacterDAO.merge(data.getCharacter());
            members.add(createMember(
                data.getTeam(), character, data.getRace()));
        }
    }

}
