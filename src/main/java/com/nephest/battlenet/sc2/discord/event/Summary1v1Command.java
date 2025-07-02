// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.event;

import com.nephest.battlenet.sc2.discord.Discord;
import com.nephest.battlenet.sc2.discord.DiscordBootstrap;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.inner.ConvertedTeamHistoryStaticData;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistoryDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistorySummary;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyId;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyIdEntry;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import com.nephest.battlenet.sc2.model.local.inner.TypedTeamHistorySummaryData;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderCharacterDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.util.MiscUtil;
import com.nephest.battlenet.sc2.web.service.SearchService;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.object.entity.Message;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.lang.Nullable;
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
    public static final Comparator<TeamHistorySummary<ConvertedTeamHistoryStaticData, TypedTeamHistorySummaryData>> DEFAULT_COMPARATOR =
        Comparator.comparing(s->s.summary().ratingLast(), Comparator.reverseOrder());

    public static final Set<TeamHistoryDAO.StaticColumn> STATIC_HISTORY_COLUMNS
        = Collections.unmodifiableSet(EnumSet.of(
            TeamHistoryDAO.StaticColumn.QUEUE_TYPE,
            TeamHistoryDAO.StaticColumn.TEAM_TYPE,
            TeamHistoryDAO.StaticColumn.REGION,
            TeamHistoryDAO.StaticColumn.LEGACY_ID
    ));
    public static final Set<TeamHistoryDAO.SummaryColumn> SUMMARY_HISTORY_COLUMNS
        = Collections.unmodifiableSet(EnumSet.of(
            TeamHistoryDAO.SummaryColumn.GAMES,
            TeamHistoryDAO.SummaryColumn.RATING_LAST,
            TeamHistoryDAO.SummaryColumn.RATING_AVG,
            TeamHistoryDAO.SummaryColumn.RATING_MAX
    ));

    private final TeamDAO teamDAO;
    private final LadderSearchDAO ladderSearchDAO;
    private final TeamHistoryDAO teamHistoryDAO;
    private final SearchService searchService;
    private final DiscordBootstrap discordBootstrap;
    private final ConversionService sc2ConversionService;

    @Autowired
    public Summary1v1Command
    (
        TeamDAO teamDAO,
        LadderSearchDAO ladderSearchDAO,
        TeamHistoryDAO teamHistoryDAO,
        SearchService searchService,
        DiscordBootstrap discordBootstrap,
        @Qualifier("sc2StatsConversionService") ConversionService sc2ConversionService
    )
    {
        this.teamDAO = teamDAO;
        this.ladderSearchDAO = ladderSearchDAO;
        this.teamHistoryDAO = teamHistoryDAO;
        this.searchService = searchService;
        this.discordBootstrap = discordBootstrap;
        this.sc2ConversionService = sc2ConversionService;
    }

    public Mono<Message> handle
    (
        ApplicationCommandInteractionEvent evt,
        @Nullable String additionalDescription,
        Region region,
        Race race,
        long depth,
        String... names
    )
    {

        for(String name : names)
        {
            if(name == null || name.isBlank()) continue;
            Mono<Message> msg = handle(evt, name, region, race, depth, additionalDescription);
            if(msg != null) return msg;
        }

        return notFound(evt, additionalDescription, region, race, depth, names);
    }

    public Mono<Message> handle
    (
        ApplicationCommandInteractionEvent evt,
        Region region,
        Race race,
        long depth,
        String... names
    )
    {
        return handle(evt, null, region, race, depth, names);
    }

    private Mono<Message> handle
    (
        ApplicationCommandInteractionEvent evt,
        String name,
        Region region,
        Race race,
        long depth,
        @Nullable String additionalDescription
    )
    {
        LadderCharacterDAO.SearchType searchType = LadderCharacterDAO.SearchType.from(name);
        int maxLines = MAX_LINES.getOrDefault(searchType, DiscordBootstrap.DEFAULT_LINES);

        List<LadderTeamMember> characters =
            searchService.findDistinctCharacters(name).stream()
            .filter(generateCharacterFilter(searchType, region))
            .limit(race == null ? maxLines : CHARACTER_LIMIT)
            .map(LadderDistinctCharacter::getMembers)
            .toList();
        if(characters.isEmpty()) return null;

        Set<TeamLegacyUid> uids = characters.stream()
            .map(LadderTeamMember::getCharacter)
            .map(c->new TeamLegacyUid(
                QueueType.LOTV_1V1,
                TeamType.ARRANGED,
                c.getRegion(),
                TeamLegacyId.standard(List.of(
                    race == null
                        ? new TeamLegacyIdEntry
                            (
                                c.getRealm(),
                                c.getBattlenetId(),
                                true
                            )
                        : new TeamLegacyIdEntry
                            (
                                c.getRealm(),
                                c.getBattlenetId(),
                                race
                            )
                ))
            ))
            .collect(Collectors.toSet());
        List<TeamHistorySummary<ConvertedTeamHistoryStaticData, TypedTeamHistorySummaryData>> summaries
            = teamHistoryDAO.findSummary
            (
                Set.copyOf(teamDAO.findIdsByLegacyUids(uids, null, null)),
                SC2Pulse.offsetDateTime().minusDays(depth), null,
                STATIC_HISTORY_COLUMNS, SUMMARY_HISTORY_COLUMNS,
                TeamHistoryDAO.GroupMode.LEGACY_UID
            ).stream()
                .map(TeamHistorySummary::cast)
                .map(c->TeamHistorySummary.convert(c, sc2ConversionService))
                .sorted(DEFAULT_COMPARATOR)
                .limit(maxLines)
                .toList();
        if(summaries.isEmpty()) return null;

        Map<TeamLegacyUid, LadderTeam> teams = ladderSearchDAO.findLegacyTeams
        (
            summaries.stream()
                .map(s->new TeamLegacyUid(
                    s.staticData().queueType(),
                    s.staticData().teamType(),
                    s.staticData().region(),
                    s.staticData().legacyId()
                ))
                .collect(Collectors.toSet()),
            false
        )
            .stream()
            .collect(Collectors.toMap(LadderTeam::getLegacyUid, Function.identity()));
        if(teams.isEmpty()) return null;

        StringBuilder description = new StringBuilder();
        appendHeader(description, name, depth, maxLines, region, race, additionalDescription)
            .append("\n\n");
        int gamesDigits = summaries.stream()
            .mapToInt(s->s.summary().games())
            .map(MiscUtil::stringLength)
            .max()
            .orElseThrow();
        for(TeamHistorySummary<ConvertedTeamHistoryStaticData, TypedTeamHistorySummaryData> summary : summaries)
        {
            TeamLegacyUid uid = new TeamLegacyUid
            (
                summary.staticData().queueType(),
                summary.staticData().teamType(),
                summary.staticData().region(),
                summary.staticData().legacyId()
            );
            appendSummary(description, teams.get(uid), summary, discordBootstrap, evt, gamesDigits)
                .append("\n\n");
            if(description.length() + CONTENT_LENGTH_OFFSET > DiscordBootstrap.MESSAGE_LENGTH_MAX)
            {
                if(description.length() + MESSAGE_WAS_TRIMMED.length() <= DiscordBootstrap.MESSAGE_LENGTH_MAX)
                    description.append(MESSAGE_WAS_TRIMMED);
                break;
            }
        }
        DiscordBootstrap.trimIfLong(description);

        return evt.createFollowup().withContent(description.toString());
    }

    public static StringBuilder appendSummary
    (
        StringBuilder sb,
        LadderTeam team,
        TeamHistorySummary<ConvertedTeamHistoryStaticData, TypedTeamHistorySummaryData> summary,
        DiscordBootstrap discordBootstrap,
        ApplicationCommandInteractionEvent evt,
        long gamesDigits
    )
    {
        LadderTeamMember member = team.getMembers().get(0);
        return sb.append(discordBootstrap.generateCharacterURL(member))
            .append("\n")
            .append(DiscordBootstrap.REGION_EMOJIS.get(member.getCharacter().getRegion()))
            .append(" ").append(discordBootstrap.getLeagueEmojiOrName(evt, team.getLeagueType()))
            .append(" ").append(discordBootstrap.getRaceEmojiOrName
            (
                evt,
                summary.staticData().legacyId().getEntries().get(0).race())
            )
            .append(" | **`")
            .append(String.format("%" + gamesDigits + "d", summary.summary().games()))
            .append("`** | **")
            .append(team.getRating()).append("**")
            .append("/*").append(Math.round(summary.summary().ratingAvg()))
            .append("*/").append(summary.summary().ratingMax());
    }

    public static StringBuilder appendHeader
    (
        StringBuilder sb,
        String name,
        long depth,
        int lines,
        Region region,
        Race race,
        @Nullable String additionalDescription
    )
    {
        sb.append("**1v1 Summary**\n");
        if(additionalDescription != null) sb.append(additionalDescription).append("\n");
        appendDescription(sb, name, depth, lines, region, race);
        return sb;
    }

    private static StringBuilder appendDescription
    (StringBuilder sb, String name, long depth, int lines, Region region, Race race)
    {
        sb.append(DiscordBootstrap.sanitizeAndEscape(name)).append(", ");
        sb.append("*");
        sb.append(depth).append(" days, Top ").append(lines);
        if(region != null) sb.append(", ").append(region.getName());
        if(race != null) sb.append(", ").append(race.getName());
        sb.append("*");

        sb.append( "\n**`Games`** | **last**/*avg*/max MMR");

        return sb;
    }

    private static Predicate<LadderDistinctCharacter> generateCharacterFilter(LadderCharacterDAO.SearchType searchType, Region region)
    {
        Predicate<LadderDistinctCharacter> characterFilter =
            c->region == null || c.getMembers().getCharacter().getRegion() == region;
        if(searchType != LadderCharacterDAO.SearchType.BATTLE_TAG) characterFilter = characterFilter
            .and(c->c.getPreviousStats() != null || c.getCurrentStats() != null);
        return characterFilter;
    }

    public static Mono<Message> notFound
    (
        ApplicationCommandInteractionEvent evt,
        @Nullable String additionalDescription,
        Region region,
        Race race,
        long depth,
        String... names
    )
    {
        String notFound = appendHeader
        (
            new StringBuilder(),
            String.join(", ", names),
            depth,
            DiscordBootstrap.DEFAULT_LINES,
            region,
            race,
            additionalDescription
        )
            .append("\n\n")
            .append("**Not found**")
            .toString();
        return evt.createFollowup().withContent(notFound);
    }

}
