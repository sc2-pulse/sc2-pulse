package com.nephest.battlenet.sc2.discord;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.BaseLeague.LeagueType;
import discord4j.core.event.domain.guild.EmojisUpdateEvent;
import discord4j.core.event.domain.interaction.InteractionCreateEvent;
import discord4j.core.object.entity.GuildEmoji;
import discord4j.core.retriever.EntityRetrievalStrategy;
import reactor.core.publisher.Mono;

@Component
public class GuildEmojiStore {

    private static final Set<String> LEAGUE_AND_RACE_EMOJI_NAMES = Stream.concat(
            Arrays.stream(LeagueType.values()).map(GuildEmojiStore::toEmojiKey),
            Arrays.stream(Race.values()).map(GuildEmojiStore::toEmojiKey))
            .collect(Collectors.toUnmodifiableSet());

    public Optional<String> getGuildLeagueEmoji(
            Map<String, String> guildEmojis,
            LeagueType league) {
        return Optional.ofNullable(guildEmojis.get(toEmojiKey(league)));
    }

    public Optional<String> getGuildRaceEmoji(
            Map<String, String> guildEmojis,
            Race race) {
        return Optional.ofNullable(guildEmojis.get(toEmojiKey(race)));
    }

    @Cacheable(cacheNames = "discord-guild-emoji", key="#evt.getInteraction().getGuildId().get()?.asLong()")
    public Map<String, String> getGuildEmojis(InteractionCreateEvent evt) {
        return evt.getInteraction()
            .getGuild()
            .map(guild -> guild.getEmojis(EntityRetrievalStrategy.STORE_FALLBACK_REST))
            .flatMapMany(Function.identity())
            .filter(GuildEmojiStore::isLeagueOrRaceEmoji)
            .collectMap(GuildEmojiStore::toEmojiKey, GuildEmojiStore::renderGuildEmoji)
            .block();
    }

    @CacheEvict(cacheNames = "discord-guild-emoji", key="#evt.getGuildId().asLong()")
    public Mono<Void> removeGuildEmojis(EmojisUpdateEvent evt) {
        return Mono.empty();
    }

    private static String toEmojiKey(String name) {
        return name.toLowerCase(Locale.ENGLISH);
    }
    
    private static String toEmojiKey(LeagueType league) {
        return toEmojiKey(league.name());
    }
    
    private static String toEmojiKey(Race race) {
        return toEmojiKey(race.name());
    }
    
    private static String toEmojiKey(GuildEmoji emoji) {
        return toEmojiKey(emoji.getName());
    }

    private static boolean isLeagueOrRaceEmoji(GuildEmoji emoji) {
        return LEAGUE_AND_RACE_EMOJI_NAMES.contains(toEmojiKey(emoji));
    }

    private static String renderGuildEmoji(GuildEmoji emoji) {
        return "<:" + emoji.getName() + ":" + emoji.getId().asString() + ">";
    }
}
