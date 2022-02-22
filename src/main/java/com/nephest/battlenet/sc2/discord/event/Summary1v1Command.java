// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.event;

import com.nephest.battlenet.sc2.discord.DiscordBootstrap;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.inner.PlayerCharacterSummary;
import com.nephest.battlenet.sc2.model.local.inner.PlayerCharacterSummaryDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderCharacterDAO;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.EmbedCreateSpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(prefix = "discord", name = "token")
public class Summary1v1Command
{

    public static final String CMD_NAME = "1v1-summary";
    public static final int CHARACTER_LIMIT = 80;
    public static final long DEFAULT_DEPTH = 120;
    public static final Map<LadderCharacterDAO.SearchType, Long> MAX_DEPTH = Map.of
    (
        LadderCharacterDAO.SearchType.BATTLE_TAG, 3600L
    );
    public static final Map<LadderCharacterDAO.SearchType, Integer> MAX_LINES = Map.of
    (
        LadderCharacterDAO.SearchType.CLAN_TAG, 10
    );
    public static final Comparator<PlayerCharacterSummary> DEFAULT_COMPARATOR =
        Comparator.comparing(PlayerCharacterSummary::getRatingLast).reversed();

    private final LadderCharacterDAO ladderCharacterDAO;
    private final PlayerCharacterSummaryDAO summaryDAO;
    private final DiscordBootstrap discordBootstrap;

    @Autowired
    public Summary1v1Command
    (
        LadderCharacterDAO ladderCharacterDAO,
        PlayerCharacterSummaryDAO summaryDAO,
        DiscordBootstrap discordBootstrap
    )
    {
        this.ladderCharacterDAO = ladderCharacterDAO;
        this.summaryDAO = summaryDAO;
        this.discordBootstrap = discordBootstrap;
    }

    public Mono<Message> handle
    (ApplicationCommandInteractionEvent evt, String name, Region region, Race race, long depth)
    {
        LadderCharacterDAO.SearchType searchType = LadderCharacterDAO.SearchType.from(name);
        int maxLines = MAX_LINES.getOrDefault(searchType, DiscordBootstrap.DEFAULT_LINES);

        Map<Long, LadderTeamMember> characters = ladderCharacterDAO.findDistinctCharacters(name).stream()
            .filter(generateCharacterFilter(searchType, region))
            .limit(race == null ? maxLines : CHARACTER_LIMIT)
            .collect(Collectors.toMap(c->c.getMembers().getCharacter().getId(), LadderDistinctCharacter::getMembers));
        if(characters.isEmpty()) return DiscordBootstrap.notFoundFollowup(evt);

        List<PlayerCharacterSummary> summaries = summaryDAO
            .find
                (
                    characters.keySet().toArray(Long[]::new),
                    OffsetDateTime.now().minusDays(depth),
                    race == null ? Race.EMPTY_RACE_ARRAY : new Race[]{race}
                ).stream()
            .sorted(DEFAULT_COMPARATOR)
            .limit(maxLines)
            .collect(Collectors.toList());
        if(summaries.isEmpty()) return DiscordBootstrap.notFoundFollowup(evt);

        StringBuilder description = new StringBuilder();
        description.append(generateDescription(name, depth, maxLines, region, race)).append("\n\n");
        for(PlayerCharacterSummary summary : summaries)
        {
            LadderTeamMember member = characters.get(summary.getPlayerCharacterId());
            description.append(DiscordBootstrap.generateCharacterURL(member))
                .append("\n")
                .append(DiscordBootstrap.REGION_EMOJIS.get(member.getCharacter().getRegion()))
                .append(" ").append(discordBootstrap.getLeagueEmojiOrName(summary.getLeagueTypeLast()))
                .append(" ").append(discordBootstrap.getRaceEmojiOrName(summary.getRace()))
                .append(" | **").append(summary.getGames()).append("** | **")
                .append(summary.getRatingLast()).append("**")
                .append("/").append(summary.getRatingAvg())
                .append("/").append(summary.getRatingMax())
                .append("\n\n");
        }

        EmbedCreateSpec.Builder embed = discordBootstrap.embedBuilder()
            .title("1v1 Summary")
            .description(description.toString());
        return evt.createFollowup()
            .withEmbeds(embed.build());
    }

    private static String generateDescription(String name, long depth, int lines, Region region, Race race)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("*");
        sb.append(name).append(", ").append(depth).append(" days, Top ").append(lines);
        if(region != null) sb.append(", ").append(region.getName());
        if(race != null) sb.append(", ").append(race.getName());
        sb.append("*");

        sb.append( "\nGames | last/avg/max MMR");

        return sb.toString();
    }

    private static Predicate<LadderDistinctCharacter> generateCharacterFilter(LadderCharacterDAO.SearchType searchType, Region region)
    {
        Predicate<LadderDistinctCharacter> characterFilter =
            c->region == null || c.getMembers().getCharacter().getRegion() == region;
        if(searchType != LadderCharacterDAO.SearchType.BATTLE_TAG) characterFilter = characterFilter
            .and(c->c.getPreviousStats() != null || c.getCurrentStats() != null);
        return characterFilter;
    }

}
