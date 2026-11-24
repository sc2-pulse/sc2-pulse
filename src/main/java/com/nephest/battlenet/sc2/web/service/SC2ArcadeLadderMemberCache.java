// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.sc2arcade.SC2ArcadeLadderCharacter;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Members of one-member teams and their names for a region and season. Teams
 * don't change their members, so the members never expire. Names do.
 */
class SC2ArcadeLadderMemberCache
{

    public static final Duration NAME_TTL = Duration.ofHours(48);
    public static final Duration RETRY_DELAY_MIN = Duration.ofMinutes(5);
    public static final Duration RETRY_DELAY_MAX = Duration.ofHours(24);

    private record Name(String value, Instant fetchedAt){}

    private record RetryState(Duration delay, Instant availableAt){}

    private final Region region;
    private final Integer pulseSeasonId;
    private final Map<String, PlayerCharacterNaturalId> members =
        new ConcurrentHashMap<>();
    private final Map<PlayerCharacterNaturalId, Name> names =
        new ConcurrentHashMap<>();
    private final Map<String, RetryState> retries = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;

    public SC2ArcadeLadderMemberCache(Region region, Integer pulseSeasonId)
    {
        this.region = region;
        this.pulseSeasonId = pulseSeasonId;
    }

    public Integer getPulseSeasonId()
    {
        return pulseSeasonId;
    }

    /**
     * @return true if all rankings were read at least once
     */
    public boolean isInitialized()
    {
        return initialized;
    }

    public void setInitialized()
    {
        this.initialized = true;
    }

    public Optional<PlayerCharacterNaturalId> getMember(String teamId)
    {
        return Optional.ofNullable(members.get(teamId));
    }

    /**
     * Only complete one-member teams are stored.
     *
     * @param teamId team id
     * @param characters team members
     * @param fetchedAt fetch time
     */
    public void put
    (
        String teamId,
        List<SC2ArcadeLadderCharacter> characters,
        Instant fetchedAt
    )
    {
        for(SC2ArcadeLadderCharacter character : characters)
            character.naturalId(region).ifPresent(id->character.resolvedName()
                .ifPresent(name->names.put(id, new Name(name, fetchedAt))));
        if(characters.size() != 1) return;

        characters.get(0).naturalId(region).ifPresent(id->{
            members.put(teamId, id);
            retries.remove(teamId);
        });
    }

    public void put(String teamId, List<SC2ArcadeLadderCharacter> characters)
    {
        put(teamId, characters, SC2Pulse.instant());
    }

    public Optional<String> getName(PlayerCharacterNaturalId id)
    {
        return Optional.ofNullable(names.get(id))
            .filter(SC2ArcadeLadderMemberCache::isFresh)
            .map(Name::value);
    }

    private static boolean isFresh(Name name)
    {
        return name.fetchedAt()
            .isAfter(SC2Pulse.instant().minus(NAME_TTL));
    }

    public Set<PlayerCharacter> getCharacters()
    {
        return names.entrySet().stream()
            .filter(e->isFresh(e.getValue()))
            .map(e->PlayerCharacter.of(
                new Account(), e.getKey(), e.getValue().value()))
            .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isLookupAvailable(String teamId)
    {
        RetryState state = retries.get(teamId);
        return state == null
            || !state.availableAt().isAfter(SC2Pulse.instant());
    }

    public void lookupFailed(String teamId)
    {
        retries.compute(teamId, (id, state)->{
            Duration delay = state == null
                ? RETRY_DELAY_MIN
                : state.delay().multipliedBy(2);
            if(delay.compareTo(RETRY_DELAY_MAX) > 0) delay = RETRY_DELAY_MAX;
            return new RetryState(delay, SC2Pulse.instant().plus(delay));
        });
    }

}
