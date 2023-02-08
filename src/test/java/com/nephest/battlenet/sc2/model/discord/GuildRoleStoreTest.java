// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
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
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Role;
import discord4j.discordjson.json.ImmutableRoleData;
import discord4j.discordjson.json.RoleData;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.Range;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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


    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    public void testGetRoleMappings(boolean permissionRequired)
    {
        long guildId = 100;
        GatewayDiscordClient client = mock(GatewayDiscordClient.class);
        Flux<Role> roles = Flux.just
        (
            mockRole(client, roleBuilder().id(1L).name("Eu").build(), guildId),
            mockRole(client, roleBuilder().id(2L).name("broNze").build(), guildId),
            mockRole(client, roleBuilder().id(3L).name("metal").build(), guildId),
            mockRole(client, roleBuilder().id(4L).name("TERRAN").build(), guildId),
            mockRole(client, roleBuilder().id(5L).name("1-100MMR").build(), guildId),
            //managed roles should be skipped
            mockRole(client, roleBuilder().id(6L).name("diamond").managed(true).build(), guildId),
            //should be skipped if position is taken into account
            mockRole(client, roleBuilder().id(7L).name("master").position(100).build(), guildId)
        );
        ApplicationCommandInteractionEvent evt = mock(ApplicationCommandInteractionEvent.class);
        Interaction interaction = mock(Interaction.class);
        Guild guild = mock(Guild.class);
        when(evt.getInteraction()).thenReturn(interaction);
        when(interaction.getGuild()).thenReturn(Mono.just(guild));
        when(guild.getRoles()).thenReturn(roles);
        if(permissionRequired) stubTopRolePosition(client, guild, 10, 100);

        PulseMappings<Role> mappings = permissionRequired
            ? store.getManagedRoleMappings(evt).block()
            : store.getRoleMappings(evt).block();
        assertFalse(mappings.isEmpty());
        assertEquals(1, mappings.getRegionMappings().getMappings().size());
        assertEquals(1L, mappings.getRegionMappings().getMappings()
            .get(Region.EU).get(0).getId().asLong());

        Map<BaseLeague.LeagueType, List<Role>> leagueMappings =
            mappings.getLeagueMappings().getMappings();
        assertEquals(permissionRequired ? 4 : 5, leagueMappings.size());
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
        assertNull(leagueMappings.get(BaseLeague.LeagueType.DIAMOND));
        if(permissionRequired)
        {
            assertNull(leagueMappings.get(BaseLeague.LeagueType.MASTER));
        }
        else
        {
            assertEquals(7L, leagueMappings.get(BaseLeague.LeagueType.MASTER)
                .get(0).getId().asLong());
        }

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
            .managed(false);
    }

    public static RoleData roleData(long id, String name)
    {
        return roleBuilder().id(id).name(name).build();
    }
    
    public static Role mockRole
    (
        GatewayDiscordClient client,
        ImmutableRoleData roleData,
        long guildId
    )
    {
        Role roleSpy = spy(new Role(client, roleData, guildId));
        doReturn(Mono.just(roleData.position())).when(roleSpy).getPosition();
        return roleSpy;
    }

    private void stubTopRolePosition
    (
        GatewayDiscordClient client,
        Guild guild,
        long guildId,
        int position
    )
    {

        Role highestRole = mockRole(client, roleBuilder().id(position).name("top").position(position).build(), guildId);
        Member selfMember = mock(Member.class);
        when(selfMember.getBasePermissions())
            .thenReturn(Mono.just(PermissionSet.of(Permission.MANAGE_ROLES)));
        doReturn(Mono.just(highestRole)).when(selfMember).getHighestRole();
        when(guild.getSelfMember()).thenReturn(Mono.just(selfMember));
    }

}
