package com.nephest.battlenet.sc2.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.BaseLeague.LeagueType;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.InteractionCreateEvent;
import discord4j.core.object.command.Interaction;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.GuildEmoji;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
public class GuildEmojiStoreTest {
    
    @Mock
    private InteractionCreateEvent evt;
 
    @Mock
    private Interaction interaction;

    @Mock
    private Guild guild;

    @Mock
    private GuildEmoji diamondEmoji;

    @Mock
    private GuildEmoji terranEmoji;

    @Mock
    private GuildEmoji otherEmoji;

    private GuildEmojiStore guildEmojiStore;

    @BeforeEach
    public void beforeEach() {
        guildEmojiStore = new GuildEmojiStore();

        stub();
    }

    @Test
    public void testExistingLeagueEmoji() {
        var result = guildEmojiStore.getGuildLeagueEmoji(
            guildEmojiStore.getGuildEmojis(evt),
            LeagueType.DIAMOND);
        assertTrue(result.isPresent());
        assertEquals("<:diamond:342348>", result.get());
    }
    
    @Test
    public void testExistingRaceEmoji() {
        var result = guildEmojiStore.getGuildRaceEmoji(
            guildEmojiStore.getGuildEmojis(evt),
            Race.TERRAN);
        assertTrue(result.isPresent());
        assertEquals("<:terran:5435634>", result.get());
    }
    
    @Test
    public void testNonExistentLeagueEmoji() {
        var result = guildEmojiStore.getGuildLeagueEmoji(
            guildEmojiStore.getGuildEmojis(evt),
            LeagueType.BRONZE);
        assertTrue(result.isEmpty());
    }
    
    @Test
    public void testNonExistentRaceEmoji() {
        var result = guildEmojiStore.getGuildRaceEmoji(
            guildEmojiStore.getGuildEmojis(evt),
            Race.RANDOM);
        assertTrue(result.isEmpty());
    }

    private void stub() {
        when(evt.getInteraction()).thenReturn(interaction);
        when(interaction.getGuild()).thenReturn(Mono.just(guild));
        when(guild.getEmojis(any())).thenReturn(Flux.just(otherEmoji, terranEmoji, diamondEmoji));
        when(terranEmoji.getName()).thenReturn("terran");
        when(terranEmoji.getId()).thenReturn(Snowflake.of(5435634L));
        when(diamondEmoji.getName()).thenReturn("diamond");
        when(diamondEmoji.getId()).thenReturn(Snowflake.of(342348L));
        when(otherEmoji.getName()).thenReturn("other");
    }
    
}
