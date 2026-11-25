// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.CollectionVar;
import com.nephest.battlenet.sc2.model.local.Match;
import com.nephest.battlenet.sc2.model.local.MatchParticipant;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.SC2Map;
import com.nephest.battlenet.sc2.model.local.dao.MatchDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchParticipantDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.SC2MapDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.model.sc2arcade.SC2ArcadeLadderGame;
import com.nephest.battlenet.sc2.model.sc2arcade.SC2ArcadeLadderGamePage;
import com.nephest.battlenet.sc2.model.sc2arcade.SC2ArcadeLadderGameRecord;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/**
 * 1v1 ladder matches on top of {@link SC2ArcadeLadderAPI}. It is the only match
 * source of the regions that were enabled via {@link #addRegion(Region)}. The
 * ladder source of a region is independent, see {@link SC2ArcadeLadderService}.
 */
@Service
public class SC2ArcadeMatchService
{

    private static final Logger LOG =
        LoggerFactory.getLogger(SC2ArcadeMatchService.class);

    /**
     * These regions have no other match source, so they can't be removed.
     */
    public static final Set<Region> REQUIRED_REGIONS = Set.of(Region.CN);
    /**
     * Games are published with a delay, so the same frame is read repeatedly.
     */
    public static final Duration MATCH_LOOKBACK = Duration.ofHours(24);
    public static final Duration MAP_POOL_MATCH_FRAME = Duration.ofDays(14);

    private static final String BAILER_RESULT = "bailer";

    protected enum GameStatus
    {
        LADDER_1V1,
        MISSING_DATA,
        EXPIRED,
        INVALID_PLAYERS,
        HAS_BAILER,
        INVALID_RESULTS,
        NOT_LADDER_MAP
    }

    private record SavedPage
    (
        Set<PlayerCharacter> knownCharacters,
        int matchCount
    )
    {}

    private static final class RunStats
    {

        private final Map<GameStatus, Integer> gameCountByStatus =
            new EnumMap<>(GameStatus.class);
        private final Set<String> unknownToonHandles = new HashSet<>();

    }

    private final SC2ArcadeLadderAPI api;
    private final MatchDAO matchDAO;
    private final MatchParticipantDAO matchParticipantDAO;
    private final PlayerCharacterDAO playerCharacterDAO;
    private final SC2MapDAO mapDAO;
    private final GlobalContext globalContext;
    private final SC2ArcadeMatchService sc2ArcadeMatchService;

    private final CollectionVar<Set<Region>, Region> regions;

    @Autowired
    public SC2ArcadeMatchService
    (
        SC2ArcadeLadderAPI api,
        MatchDAO matchDAO,
        MatchParticipantDAO matchParticipantDAO,
        PlayerCharacterDAO playerCharacterDAO,
        SC2MapDAO mapDAO,
        VarDAO varDAO,
        GlobalContext globalContext,
        @Lazy SC2ArcadeMatchService sc2ArcadeMatchService
    )
    {
        this.api = api;
        this.matchDAO = matchDAO;
        this.matchParticipantDAO = matchParticipantDAO;
        this.playerCharacterDAO = playerCharacterDAO;
        this.mapDAO = mapDAO;
        this.globalContext = globalContext;
        this.sc2ArcadeMatchService = sc2ArcadeMatchService;
        this.regions = WebServiceUtil.loadRegionSetVar
        (
            varDAO,
            "match.sc2arcade.regions",
            "Loaded regions for SC2Arcade matches: {}"
        );
        regions.getValue().addAll(REQUIRED_REGIONS);
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

    /**
     * Errors are logged and skipped. The same frame is read again by the
     * next call, so there is no state.
     *
     * @return known characters of the saved matches
     */
    public Mono<Map<Region, Set<PlayerCharacter>>> update()
    {
        return WebServiceUtil.getOnErrorLogAndSkipMono(
            WebServiceUtil.blockingCallable(this::updateSync))
            .defaultIfEmpty(Map.of());
    }

    private Map<Region, Set<PlayerCharacter>> updateSync()
    {
        List<Region> updateRegions = globalContext.getActiveRegions().stream()
            .filter(this::isArcadeRegion)
            .sorted()
            .toList();
        if(updateRegions.isEmpty()) return Map.of();

        Set<String> mapPoolNames = findMapPoolNames();
        if(mapPoolNames.isEmpty())
        {
            LOG.error("1v1 map pool is empty, can't save SC2Arcade matches: {}",
                updateRegions);
            return Map.of();
        }

        Map<Region, Set<PlayerCharacter>> result = new EnumMap<>(Region.class);
        for(Region region : updateRegions)
        {
            Set<PlayerCharacter> savedCharacters = new HashSet<>();
            result.put(region, savedCharacters);
            try
            {
                updateRegion(region, mapPoolNames, savedCharacters);
            }
            catch(RuntimeException e)
            {
                LOG.error("Failed to update SC2Arcade matches: " + region, e);
            }
        }
        return result;
    }

    private Set<String> findMapPoolNames()
    {
        return mapDAO.find
        (
            BaseMatch.MatchType._1V1,
            SC2Pulse.offsetDateTime().minus(MAP_POOL_MATCH_FRAME),
            Set.of()
        )
            .stream()
            .map(SC2Map::getName)
            .collect(Collectors.toSet());
    }

    private void updateRegion
    (
        Region region,
        Set<String> mapPoolNames,
        Set<PlayerCharacter> savedCharacters
    )
    {
        Instant windowEnd = SC2Pulse.instant();
        Instant windowStart = windowEnd.minus(MATCH_LOOKBACK);
        RunStats runStats = new RunStats();
        String cursor = null;
        int matchCount = 0;
        int pageCount = 0;
        boolean hasNext = true;
        while(hasNext)
        {
            SC2ArcadeLadderGamePage page = api
                .getGames(region, windowStart, windowEnd, cursor)
                .blockOptional()
                .orElseThrow();
            if(!page.isReady())
            {
                LOG.warn("SC2Arcade games are not ready: {}", region);
                return;
            }

            SavedPage savedPage = sc2ArcadeMatchService
                .savePage(region, page, mapPoolNames, runStats);
            savedCharacters.addAll(savedPage.knownCharacters());
            matchCount += savedPage.matchCount();
            hasNext = page.hasNext();
            if(hasNext)
            {
                String nextCursor = page.navigation().next();
                if(nextCursor.equals(cursor))
                    throw new IllegalStateException(
                        "Repeated SC2Arcade game cursor: " + region);

                cursor = nextCursor;
            }
            pageCount++;
        }
        LOG.info
        (
            "Saved {} SC2Arcade matches, {} {} pages, games: {}, "
                + "unknown characters: {}",
            matchCount, region, pageCount, runStats.gameCountByStatus,
            runStats.unknownToonHandles.size()
        );
    }

    protected static GameStatus getStatus
    (
        SC2ArcadeLadderGame game,
        Region region,
        Set<String> mapPoolNames,
        OffsetDateTime minEndDate
    )
    {
        if
        (
            game.endedAt() == null
            || game.map() == null
            || game.map().name() == null
        ) return GameStatus.MISSING_DATA;
        if(game.endedAt().isBefore(minEndDate)) return GameStatus.EXPIRED;

        Set<String> toonHandles = game.players().stream()
            .map(player->getNaturalId(player, region))
            .flatMap(Optional::stream)
            .map(PlayerCharacterNaturalId::toToonHandle)
            .collect(Collectors.toSet());
        if(game.players().size() != 2 || toonHandles.size() != 2)
            return GameStatus.INVALID_PLAYERS;

        if(game.players().stream().anyMatch(
            player->BAILER_RESULT.equalsIgnoreCase(player.result())))
                return GameStatus.HAS_BAILER;
        if(!areLadder1v1Decisions(game.players().stream()
            .map(SC2ArcadeMatchService::getDecision)
            .flatMap(Optional::stream)
            .toList())) return GameStatus.INVALID_RESULTS;
        return mapPoolNames.contains(game.map().name())
            ? GameStatus.LADDER_1V1
            : GameStatus.NOT_LADDER_MAP;
    }

    private static boolean areLadder1v1Decisions
    (List<BaseMatch.Decision> decisions)
    {
        if(decisions.size() != 2) return false;

        Set<BaseMatch.Decision> distinctDecisions = Set.copyOf(decisions);
        return distinctDecisions.equals(Set.of(BaseMatch.Decision.TIE))
            || distinctDecisions.equals(
                Set.of(BaseMatch.Decision.WIN, BaseMatch.Decision.LOSS));
    }

    private static Optional<PlayerCharacterNaturalId> getNaturalId
    (
        SC2ArcadeLadderGame.Player player,
        Region region
    )
    {
        return Optional.ofNullable(player.character())
            .flatMap(character->character.naturalId(region));
    }

    private static Optional<BaseMatch.Decision> getDecision
    (SC2ArcadeLadderGame.Player player)
    {
        try
        {
            return Optional.of(BaseMatch.Decision.from(player.result()));
        }
        catch(IllegalArgumentException e)
        {
            //unknown or empty result
            return Optional.empty();
        }
    }

    //the DB type is smallint
    @Nullable
    private static Integer getDuration(SC2ArcadeLadderGame game)
    {
        Integer duration = game.durationSeconds();
        return duration == null || duration < 0 || duration > Short.MAX_VALUE
            ? null
            : duration;
    }

    private Map<String, PlayerCharacter> findCharactersByToonHandle
    (
        Region region,
        Collection<SC2ArcadeLadderGame> games
    )
    {
        Set<PlayerCharacterNaturalId> naturalIds = games.stream()
            .flatMap(game->game.players().stream())
            .map(player->getNaturalId(player, region))
            .flatMap(Optional::stream)
            .collect(Collectors.toSet());
        return playerCharacterDAO
            .find(Set.copyOf(
                playerCharacterDAO.findIdsByNaturalIds(naturalIds)))
            .stream()
            .collect(Collectors.toMap(
                PlayerCharacterNaturalId::toToonHandle,
                Function.identity()
            ));
    }

    //can conflict with other match updates, same as MatchService
    @Transactional @Retryable
    protected SavedPage savePage
    (
        Region region,
        SC2ArcadeLadderGamePage page,
        Set<String> mapPoolNames,
        RunStats runStats
    )
    {
        OffsetDateTime minEndDate = SC2Pulse.offsetDateTime()
            .minusDays(MatchDAO.TTL_DAYS);
        List<SC2ArcadeLadderGame> games = new ArrayList<>();
        Map<GameStatus, Integer> gameCountByStatus =
            new EnumMap<>(GameStatus.class);
        for(SC2ArcadeLadderGameRecord record : page.items())
        {
            GameStatus status = getStatus(
                record.game(), region, mapPoolNames, minEndDate);
            gameCountByStatus.merge(status, 1, Integer::sum);
            if(status == GameStatus.LADDER_1V1) games.add(record.game());
        }

        Map<String, PlayerCharacter> charactersByToonHandle =
            findCharactersByToonHandle(region, games);
        Set<String> unknownToonHandles = new HashSet<>();
        Map<SC2ArcadeLadderGame, Map<PlayerCharacter, BaseMatch.Decision>>
            decisionsByGame = new HashMap<>();
        for(SC2ArcadeLadderGame game : games)
        {
            Map<PlayerCharacter, BaseMatch.Decision> decisionsByCharacter =
                new HashMap<>();
            for(SC2ArcadeLadderGame.Player player : game.players())
            {
                String toonHandle = getNaturalId(player, region).orElseThrow()
                    .toToonHandle();
                PlayerCharacter character =
                    charactersByToonHandle.get(toonHandle);
                if(character == null)
                {
                    unknownToonHandles.add(toonHandle);
                }
                else
                {
                    decisionsByCharacter
                        .put(character, getDecision(player).orElseThrow());
                }
            }
            if(!decisionsByCharacter.isEmpty())
                decisionsByGame.put(game, decisionsByCharacter);
        }
        int matchCount = saveMatches(region, decisionsByGame);
        //the transaction can be retried, so update the stats at the end
        gameCountByStatus.forEach((status, gameCount)->runStats
            .gameCountByStatus.merge(status, gameCount, Integer::sum));
        runStats.unknownToonHandles.addAll(unknownToonHandles);
        return new SavedPage
        (
            decisionsByGame.values().stream()
                .flatMap(decisionsByCharacter->decisionsByCharacter
                    .keySet()
                    .stream())
                .collect(Collectors.toSet()),
            matchCount
        );
    }

    //natural id order prevents deadlocks
    private int saveMatches
    (
        Region region,
        Map<SC2ArcadeLadderGame, Map<PlayerCharacter, BaseMatch.Decision>>
            decisionsByGame
    )
    {
        Map<String, Integer> mapIds = mapDAO
            .merge(decisionsByGame.keySet().stream()
                .map(game->new SC2Map(null, game.map().name()))
                .sorted(SC2Map.NATURAL_ID_COMPARATOR)
                .collect(Collectors.toCollection(LinkedHashSet::new)))
            .stream()
            .collect(Collectors.toMap(SC2Map::getName, SC2Map::getId));
        Map<SC2ArcadeLadderGame, Match> matches = decisionsByGame.keySet()
            .stream()
            .collect(Collectors.toMap(Function.identity(), game->new Match
            (
                null,
                game.endedAt(),
                BaseMatch.MatchType._1V1,
                mapIds.get(game.map().name()),
                region,
                getDuration(game)
            )));
        Map<Match, Match> savedMatches = matchDAO
            .merge(matches.values().stream()
                .sorted(Match.NATURAL_ID_COMPARATOR)
                .collect(Collectors.toCollection(LinkedHashSet::new)))
            .stream()
            .collect(Collectors.toMap(
                Function.identity(),
                Function.identity()
            ));
        matchParticipantDAO.merge(decisionsByGame.entrySet().stream()
            .flatMap(entry->entry.getValue().entrySet().stream()
                .map(participant->new MatchParticipant
                (
                    savedMatches.get(matches.get(entry.getKey())).getId(),
                    participant.getKey().getId(),
                    participant.getValue()
                )))
            .sorted(Comparator.comparing(MatchParticipant::getMatchId)
                .thenComparing(MatchParticipant::getPlayerCharacterId))
            .collect(Collectors.toCollection(LinkedHashSet::new)));
        return savedMatches.size();
    }

}
