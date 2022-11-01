// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.discord.GuildRoleStore;
import com.nephest.battlenet.sc2.discord.PulseMappings;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.object.command.Interaction;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Role;
import discord4j.discordjson.json.ImmutableRoleData;
import discord4j.discordjson.json.RoleData;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.Range;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class GuildRoleStoreTest
{

    private GuildRoleStore store;

    @BeforeEach
    public void beforeEach()
    {
        store = new GuildRoleStore();
    }

    @Test
    public void testGetRoleMappings()
    {
        long guildId = 100;
        GatewayDiscordClient client = mock(GatewayDiscordClient.class);
        Flux<Role> roles = Flux.just
        (
            new Role(client, roleBuilder().id(1L).name("Eu").build(), guildId),
            new Role(client, roleBuilder().id(2L).name("broNze").build(), guildId),
            new Role(client, roleBuilder().id(3L).name("metal").build(), guildId),
            new Role(client, roleBuilder().id(4L).name("TERRAN").build(), guildId),
            new Role(client, roleBuilder().id(5L).name("1-100MMR").build(), guildId)
        );
        ApplicationCommandInteractionEvent evt = mock(ApplicationCommandInteractionEvent.class);
        Interaction interaction = mock(Interaction.class);
        Guild guild = mock(Guild.class);
        when(evt.getInteraction()).thenReturn(interaction);
        when(interaction.getGuild()).thenReturn(Mono.just(guild));
        when(guild.getRoles()).thenReturn(roles);

        PulseMappings<Role> mappings = store.getRoleMappings(evt);
        assertFalse(mappings.isEmpty());
        assertEquals(1, mappings.getRegionMappings().getMappings().size());
        assertEquals(1L, mappings.getRegionMappings().getMappings()
            .get(Region.EU).get(0).getId().asLong());

        Map<BaseLeague.LeagueType, List<Role>> leagueMappings =
            mappings.getLeagueMappings().getMappings();
        assertEquals(4, leagueMappings.size());
        assertEquals(2L, leagueMappings.get(BaseLeague.LeagueType.BRONZE)
            .get(0).getId().asLong());
        assertEquals(3L, leagueMappings.get(BaseLeague.LeagueType.BRONZE)
            .get(1).getId().asLong());
        assertEquals(3L, leagueMappings.get(BaseLeague.LeagueType.SILVER)
            .get(0).getId().asLong());
        assertEquals(3L, leagueMappings.get(BaseLeague.LeagueType.GOLD)
            .get(0).getId().asLong());
        assertEquals(3L, leagueMappings.get(BaseLeague.LeagueType.PLATINUM)
            .get(0).getId().asLong());

        assertEquals(1, mappings.getRaceMappings().getMappings().size());
        assertEquals(4L, mappings.getRaceMappings().getMappings()
            .get(Race.TERRAN).get(0).getId().asLong());

        assertEquals(1, mappings.getRatingMappings().getMappings().size());
        Map.Entry<Range<Integer>, List<Role>> ratingRangeEntry = mappings
            .getRatingMappings().getMappings().entrySet().stream()
            .findAny()
            .orElseThrow();
        assertEquals(1, ratingRangeEntry.getValue().size());
        assertEquals(5L, ratingRangeEntry.getValue().get(0).getId().asLong());
        assertEquals(1, ratingRangeEntry.getKey().getMinimum()); //min value is included
        assertEquals(99, ratingRangeEntry.getKey().getMaximum()); //max value is excluded
    }

    public static ImmutableRoleData.Builder roleBuilder()
    {
        return RoleData.builder()
            .color(1)
            .hoist(true)
            .permissions(1L)
            .mentionable(true)
            .position(1)
            .managed(true);
    }

    public static RoleData roleData(long id, String name)
    {
        return roleBuilder().id(id).name(name).build();
    }

}
