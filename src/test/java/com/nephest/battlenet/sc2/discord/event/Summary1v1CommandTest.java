// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.config.CommonBeanConfig;
import com.nephest.battlenet.sc2.discord.DiscordBootstrap;
import com.nephest.battlenet.sc2.discord.DiscordTestUtil;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.inner.RawTeamHistoryStaticData;
import com.nephest.battlenet.sc2.model.local.inner.RawTeamHistorySummaryData;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistoryDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamHistorySummary;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyId;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyIdEntry;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderPlayerSearchStats;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.web.service.SearchService;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.spec.InteractionFollowupCreateMono;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.ConversionService;

@ExtendWith(MockitoExtension.class)
public class Summary1v1CommandTest
{

    @Mock
    private TeamDAO teamDAO;

    @Mock
    private LadderSearchDAO ladderSearchDAO;

    @Mock
    private TeamHistoryDAO teamHistoryDAO;

    @Mock
    private SearchService searchService;

    @Mock
    private DiscordBootstrap discordBootstrap;

    @Mock
    private ChatInputInteractionEvent evt;

    @Mock
    private InteractionFollowupCreateMono followup;

    @Captor
    private ArgumentCaptor<String> contentCaptor;

    @Captor
    private ArgumentCaptor<OffsetDateTime> depthCaptor;

    private Summary1v1Command cmd;

    private final ConversionService sc2ConversionService
        = new CommonBeanConfig().sc2StatsConversionService();

    @BeforeEach
    public void beforeEach()
    {
        when(evt.createFollowup()).thenReturn(followup);
        when(followup.withContent(anyString())).thenReturn(mock(InteractionFollowupCreateMono.class));
        cmd = new Summary1v1Command
        (
            teamDAO,
            ladderSearchDAO,
            teamHistoryDAO,
            searchService,
            discordBootstrap,
            sc2ConversionService
        );
    }

    @ValueSource(strings = {"term", "`term`", "te`rm"})
    @ParameterizedTest
    public void test(String term)
    {
        stub(term);

        cmd.handle
        (
            evt,
            "Additional description",
            Region.EU,
            Race.TERRAN,
            100,
            "emptyTerm", term
        );

        verify(teamHistoryDAO).findSummary
        (
            anySet(),
            depthCaptor.capture(),
            any(),
            anySet(),
            anySet(),
            any()
        );
        //verify correct depth, 10 seconds to run the test just in case
        assertTrue
        (
            SC2Pulse.offsetDateTime().minusDays(100).toEpochSecond()
            - depthCaptor.getValue().toEpochSecond()
            < 10
        );

        verify(followup).withContent(contentCaptor.capture());
        String content = contentCaptor.getValue();

        //verify output
        StringBuilder sb = new StringBuilder()
            .append("**1v1 Summary**\n")
            .append("Additional description\n")
            .append("`term`, *100 days, Top 5, EU, Terran*\n**`Games`** | **last**/*avg*/max MMR\n\n");
        for(int i = 3; i > -1; i--)
        {
            sb.append(String.format(
                "url%1$s\n" //web link
                    + DiscordBootstrap.REGION_EMOJIS.get(Region.EU) + " diamond terran" //region, league, race
                    + " | **`%4$s`** | " //games
                    + "**%1$s**/*%2$s*/%3$s\n\n", //last, avg, max mmr
                i, i * 2, i * 3, i == 3 ? "9999" : "   " + i)); //stubbed values

        }
        assertEquals(sb.toString(), content);
    }

    private List<TeamHistorySummary<RawTeamHistoryStaticData, RawTeamHistorySummaryData>> generateSummaries
    (
        int count
    )
    {
        return IntStream.range(0, count)
            .mapToObj(i->new TeamHistorySummary<>(
                new RawTeamHistoryStaticData
                (
                    Map.of
                    (
                        TeamHistoryDAO.StaticColumn.QUEUE_TYPE, QueueType.LOTV_1V1.getId(),
                        TeamHistoryDAO.StaticColumn.TEAM_TYPE, TeamType.ARRANGED.getId(),
                        TeamHistoryDAO.StaticColumn.REGION, Region.EU.getId(),
                        TeamHistoryDAO.StaticColumn.LEGACY_ID,
                        TeamLegacyId.standard(List.of(
                            new TeamLegacyIdEntry(0, (long) i, Race.TERRAN)
                        )).getId()
                    )
                ),
                new RawTeamHistorySummaryData
                (
                    Map.of
                    (
                        TeamHistoryDAO.SummaryColumn.GAMES, i == 3 ? 9999 : i,
                        TeamHistoryDAO.SummaryColumn.RATING_LAST, i,
                        TeamHistoryDAO.SummaryColumn.RATING_AVG, i * 2.0d,
                        TeamHistoryDAO.SummaryColumn.RATING_MAX, i * 3
                    )
                )
            ))
            .toList();
    }

    private List<LadderTeam> generateTeams(List<LadderDistinctCharacter> characters)
    {
        return LongStream.range(0, characters.size())
            .mapToObj(i->new LadderTeam(
                i,
                1,
                Region.EU,
                new BaseLeague
                (
                    BaseLeague.LeagueType.DIAMOND,
                    QueueType.LOTV_1V1,
                    TeamType.ARRANGED
                ),
                BaseLeagueTier.LeagueTierType.FIRST,
                TeamLegacyId.standard(List.of(
                    new TeamLegacyIdEntry(0, i, Race.TERRAN)
                )),
                1,
                i,
                1, 2, 3, 4,
                null, null, null,
                List.of(characters.get((int) i).getMembers()),
                null
            ))
                .toList();
    }

    private void stub(String term)
    {
        List<LadderDistinctCharacter> characters = new ArrayList<>();
        for(int i = 0; i < 5; i++) characters.add(DiscordTestUtil.createSimpleCharacter(
            "tag#" + i, "name#" + i, (long) i, "proName" + i, "clan" + i, "proTeam" + i, Region.EU,
            i, i
        ));
        //excluded by region filter
        characters.add(DiscordTestUtil.createSimpleCharacter(
            "tag#6", "name#6", 6L, "6", "clan6", "proTeam6", Region.US, 6, 6
        ));
        when(searchService.findDistinctCharacters("emptyTerm")).thenReturn(List.of());
        when(searchService.findDistinctCharacters(term)).thenReturn(characters);

        List<TeamLegacyUid> uids = LongStream.range(0, 5)
            .boxed()
            .map(i-> new TeamLegacyUid(
                QueueType.LOTV_1V1,
                TeamType.ARRANGED,
                Region.EU,
                TeamLegacyId.standard(List.of(
                    new TeamLegacyIdEntry(0, i, Race.TERRAN)
                ))
            ))
            .toList();
        List<Long> teamIds = List.of(1L);
        when(teamDAO.findIdsByLegacyUids(Set.copyOf(uids), null, null))
            .thenReturn(teamIds);
        when(teamHistoryDAO.findSummary(
            eq(Set.copyOf(teamIds)),
            any(), isNull(),
            eq(EnumSet.of(
                TeamHistoryDAO.StaticColumn.QUEUE_TYPE,
                TeamHistoryDAO.StaticColumn.TEAM_TYPE,
                TeamHistoryDAO.StaticColumn.REGION,
                TeamHistoryDAO.StaticColumn.LEGACY_ID
            )),
            eq(EnumSet.of(
                TeamHistoryDAO.SummaryColumn.GAMES,
                TeamHistoryDAO.SummaryColumn.RATING_LAST,
                TeamHistoryDAO.SummaryColumn.RATING_AVG,
                TeamHistoryDAO.SummaryColumn.RATING_MAX
            )),
            eq(TeamHistoryDAO.GroupMode.LEGACY_UID)
        )).thenReturn(generateSummaries(4));
        when(ladderSearchDAO.findLegacyTeams(Set.copyOf(uids.subList(0, 4)), false))
            .thenReturn(generateTeams(characters.subList(0, 4)));
        when(discordBootstrap.getLeagueEmojiOrName(evt, BaseLeague.LeagueType.DIAMOND)).thenReturn("diamond");
        when(discordBootstrap.getRaceEmojiOrName(evt, Race.TERRAN)).thenReturn("terran");
        when(discordBootstrap.generateCharacterURL(any()))
            .thenAnswer(inv->"url" + inv.getArgument(0, LadderTeamMember.class).getAccount().getId());
    }

    @Test
    public void whenNotFound_thenShowCustomMessage()
    {
        when(searchService.findDistinctCharacters(any())).thenReturn(List.of());

        cmd.handle
        (
            evt,
            "Additional description",
            Region.EU,
            Race.TERRAN,
            120,
            "name1", "name2"
        );
        verify(followup).withContent(contentCaptor.capture());
        String expectedResult =
            "**1v1 Summary**\n"
            + "Additional description\n"
            + "`name1, name2`, *120 days, Top 5, EU, Terran*\n"
            + "**`Games`** | **last**/*avg*/max MMR\n\n"

            + "**Not found**";
        assertEquals(expectedResult, contentCaptor.getValue());
    }

    @Test
    public void whenNonBattleTagSearch_thenProfilesWithoutRecentStatsAreSkipped()
    {
        String searchTerm = "searchTerm";
        //profiles with recent stats
        List<LadderDistinctCharacter> characters = new ArrayList<>(3);
        for(int i = 0; i < 2; i++) characters.add(DiscordTestUtil.createSimpleCharacter(
            "tag#" + i, "name#" + i, (long) i, "proName" + i, "clan" + i, "proTeam" + i, Region.EU,
            i, i
        ));
        //profile without recent stats must be skipped
        characters.add(new LadderDistinctCharacter(
            BaseLeague.LeagueType.GRANDMASTER,
            0,
            new Account(3L, Partition.GLOBAL, "tag#3"),
            new PlayerCharacter(3L, 3L, Region.EU, 3L, 0, "name#3"),
            null,
            null, null, null,
            null,
            0, 0, 0, 0, 0,
            new LadderPlayerSearchStats(null, null, null),
            new LadderPlayerSearchStats(null, null, null)
        ));
        when(searchService.findDistinctCharacters(searchTerm)).thenReturn(characters);

        //verify that legacyUids were generated from profiles with stats
        when(teamDAO.findIdsByLegacyUids(
            LongStream.range(0, 2)
                .mapToObj(i->new TeamLegacyUid(
                    QueueType.LOTV_1V1,
                    TeamType.ARRANGED,
                    Region.EU,
                    TeamLegacyId.standard(List.of(new TeamLegacyIdEntry(0, i, Race.TERRAN)))
                ))
                .collect(Collectors.toSet()),
            null,
            null
        ))
            .thenReturn(List.of());

        cmd.handle
        (
            evt,
            "Additional description",
            Region.EU,
            Race.TERRAN,
            100L,
            searchTerm
        );
    }

}
