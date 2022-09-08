// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.event;

import com.nephest.battlenet.sc2.discord.Discord;
import com.nephest.battlenet.sc2.discord.DiscordBootstrap;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.inner.PlayerCharacterSummary;
import com.nephest.battlenet.sc2.model.local.inner.PlayerCharacterSummaryDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderCharacterDAO;
import com.nephest.battlenet.sc2.util.MiscUtil;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.object.entity.Message;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Discord
public class Summary1v1Command
{

    public static final String CMD_NAME = "1v1-summary";
    public static final int CHARACTER_LIMIT = 80;
    public static final long DEFAULT_DEPTH = 120;
    public static final int CONTENT_LENGTH_OFFSET = 250;
    public static final String MESSAGE_WAS_TRIMMED = "*Message has been trimmed*";
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

    public Mono<Message> handle(ApplicationCommandInteractionEvent evt, Region region, Race race, long depth, String... names)
    {
        if(names.length == 0) return DiscordBootstrap.notFoundFollowup(evt);

        for(String name : names)
        {
            if(name == null || name.isBlank()) continue;
            Mono<Message> msg = handle(evt, name, region, race, depth);
            if(msg != null) return msg;
        }

        return DiscordBootstrap.notFoundFollowup(evt);
    }

    private Mono<Message> handle
    (ApplicationCommandInteractionEvent evt, String name, Region region, Race race, long depth)
    {
        LadderCharacterDAO.SearchType searchType = LadderCharacterDAO.SearchType.from(name);
        int maxLines = MAX_LINES.getOrDefault(searchType, DiscordBootstrap.DEFAULT_LINES);

        Map<Long, LadderTeamMember> characters = ladderCharacterDAO.findDistinctCharacters(name).stream()
            .filter(generateCharacterFilter(searchType, region))
            .limit(race == null ? maxLines : CHARACTER_LIMIT)
            .collect(Collectors.toMap(c->c.getMembers().getCharacter().getId(), LadderDistinctCharacter::getMembers));
        if(characters.isEmpty()) return null;

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
        if(summaries.isEmpty()) return null;

        StringBuilder description = new StringBuilder()
            .append("**1v1 Summary**\n")
            .append(generateDescription(name, depth, maxLines, region, race)).append("\n\n");
        int gamesDigits = summaries.stream()
            .mapToInt(PlayerCharacterSummary::getGames)
            .map(MiscUtil::stringLength)
            .max()
            .orElseThrow();
        for(PlayerCharacterSummary summary : summaries)
        {
            LadderTeamMember member = characters.get(summary.getPlayerCharacterId());
            description.append(DiscordBootstrap.generateCharacterURL(member))
                .append("\n")
                .append(DiscordBootstrap.REGION_EMOJIS.get(member.getCharacter().getRegion()))
                .append(" ").append(discordBootstrap.getLeagueEmojiOrName(evt, summary.getLeagueTypeLast()))
                .append(" ").append(discordBootstrap.getRaceEmojiOrName(evt, summary.getRace()))
                .append(" | **`")
                .append(String.format("%" + gamesDigits + "d", summary.getGames()))
                .append("`** | **")
                .append(summary.getRatingLast()).append("**")
                .append("/*").append(summary.getRatingAvg())
                .append("*/").append(summary.getRatingMax())
                .append("\n\n");
            if(description.length() + CONTENT_LENGTH_OFFSET > DiscordBootstrap.MESSAGE_LENGTH_MAX)
            {
                if(description.length() + MESSAGE_WAS_TRIMMED.length() <= DiscordBootstrap.MESSAGE_LENGTH_MAX)
                    description.append(MESSAGE_WAS_TRIMMED);
                break;
            }
        }
        if(description.length() > DiscordBootstrap.MESSAGE_LENGTH_MAX)
            description.setLength(DiscordBootstrap.MESSAGE_LENGTH_MAX);

        return evt.createFollowup().withContent(description.toString());
    }

    private static String generateDescription(String name, long depth, int lines, Region region, Race race)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("*");
        sb.append(name).append(", ").append(depth).append(" days, Top ").append(lines);
        if(region != null) sb.append(", ").append(region.getName());
        if(race != null) sb.append(", ").append(race.getName());
        sb.append("*");

        sb.append( "\n**`Games`** | **last**/*avg*/max MMR");

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
