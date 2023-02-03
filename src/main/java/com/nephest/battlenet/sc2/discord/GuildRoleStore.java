// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.util.MiscUtil;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.event.domain.role.RoleCreateEvent;
import discord4j.core.event.domain.role.RoleDeleteEvent;
import discord4j.core.event.domain.role.RoleUpdateEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Role;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Discord @Component
public class GuildRoleStore
{

    public static final String DELIMITER = ", ";
    public static final Function<Integer, Integer> INTEGER_SUBTRACTOR = i->i-1;

    private static final Function<Role, List<Region>> REGION_MAPPER =
        role->MiscUtil.findByAnyName(Region.ALL_NAMES_MAP, role.getName());
    private static final Function<Role, List<BaseLeague.LeagueType>> LEAGUE_MAPPER =
        role->MiscUtil.findByAnyName(BaseLeague.LeagueType.ALL_NAMES_MAP, role.getName());
    private static final List<Race> EMPTY_RACE_LIST = List.of();
    private static final Function<Role, List<Race>> RACE_MAPPER =
        role->Race.optionalFrom(role.getName()).map(List::of).orElse(EMPTY_RACE_LIST);
    private static final Function<Role, List<Range<Integer>>> RATING_RANGE_MAPPER =
        role->parseRange(role.getName(), "mmr").map(List::of).orElse(List.of());

    public static final PulseMappings<Role> EMPTY_MAPPING =
        new PulseMappings<>(Map.of(), Map.of(), Map.of(), Map.of(), Role::getName, DELIMITER);

    @Cacheable(cacheNames = "discord-guild-roles", key="#evt.getInteraction().getGuildId().get()?.asLong()")
    public PulseMappings<Role> getRoleMappings(ApplicationCommandInteractionEvent evt)
    {
        List<Role> roles = evt
            .getInteraction()
            .getGuild()
            .flatMapMany(Guild::getRoles)
            .filter(r->!r.isManaged())
            .collectList()
            .block();
        return getRoleMappings(roles);
    }

    @Cacheable(cacheNames = "discord-guild-roles", key="#guild.getId().asLong()")
    public PulseMappings<Role> getRoleMappings(Guild guild)
    {
        List<Role> roles = guild
            .getRoles()
            .filter(r->!r.isManaged())
            .collectList()
            .block();
        return getRoleMappings(roles);
    }

    @CacheEvict(cacheNames = "discord-guild-roles", key="#evt.getGuildId().asLong()")
    public Mono<Void> removeRoles(RoleCreateEvent evt)
    {
        return Mono.empty();
    }

    @CacheEvict(cacheNames = "discord-guild-roles", key="#evt.getGuildId().asLong()")
    public Mono<Void> removeRoles(RoleDeleteEvent evt)
    {
        return Mono.empty();
    }

    @CacheEvict(cacheNames = "discord-guild-roles", key="#evt.getCurrent().getGuildId().asLong()")
    public Mono<Void> removeRoles(RoleUpdateEvent evt)
    {
        return Mono.empty();
    }

    public static PulseMappings<Role> getRoleMappings(Collection<? extends Role> roles)
    {
        PulseMappings<Role> mapping =  new PulseMappings<>
        (
            createPulseEnumMap(roles, REGION_MAPPER),
            createPulseEnumMap(roles, LEAGUE_MAPPER),
            createPulseEnumMap(roles, RACE_MAPPER),
            createPulseMap(roles, RATING_RANGE_MAPPER),
            Role::getMention,
            DELIMITER
        );
        return mapping.isEmpty() ? EMPTY_MAPPING : mapping;
    }

    private static <T extends Enum<T>> Map<T, List<Role>> createPulseEnumMap
    (
        Collection<? extends Role> roles,
        Function<Role, List<T>> mapper
    )
    {
        Map<T, List<Role>> map = createPulseMap(roles, mapper);
        return map.isEmpty() ? map : new EnumMap<>(map);
    }

    public static <T> Map<T, List<Role>> createPulseMap
    (
        Collection<? extends Role> roles,
        Function<Role, List<T>> mapper
    )
    {
        return roles.stream()
            .flatMap(r->mapper.apply(r).stream().map(t->new ImmutablePair<>(t, r)))
            .filter(p->p.getLeft() != null)
            .collect(Collectors.groupingBy(ImmutablePair::getLeft,
                LinkedHashMap::new,
                Collectors.mapping(Pair::getRight, Collectors.toList())));
    }

    public static boolean canBeRange(String input, String rangeSuffix)
    {
        return input.indexOf("-") > 0 //ix0 can be int sign
            && input.length() >= rangeSuffix.length() + 3 //3 is min range length, i.e 0-1
            && input.toLowerCase().endsWith(rangeSuffix.toLowerCase());
    }

    public static Optional<Range<Integer>> parseRange(String input, String rangeSuffix)
    {
        if(!canBeRange(input, rangeSuffix)) return Optional.empty();

        try
        {
            Range<Integer> range = MiscUtil.parseRange
            (
                input.substring(0, input.length() - rangeSuffix.length()),
                Integer::valueOf,
                INTEGER_SUBTRACTOR,
                false
            );
            return Optional.of(range);
        }
        catch (RuntimeException ex)
        {
            return Optional.empty();
        }
    }

}
