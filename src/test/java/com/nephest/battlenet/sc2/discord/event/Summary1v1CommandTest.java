// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.discord.DiscordBootstrap;
import com.nephest.battlenet.sc2.discord.DiscordTestUtil;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.inner.PlayerCharacterSummary;
import com.nephest.battlenet.sc2.model.local.inner.PlayerCharacterSummaryDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderCharacterDAO;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.spec.InteractionFollowupCreateMono;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class Summary1v1CommandTest
{

    @Mock
    private LadderCharacterDAO ladderCharacterDAO;

    @Mock
    private PlayerCharacterSummaryDAO summaryDAO;

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

    @Test
    public void test()
    {
        stub();

        Summary1v1Command cmd = new Summary1v1Command(ladderCharacterDAO, summaryDAO, discordBootstrap);
        cmd.handle(evt, Region.EU, Race.TERRAN, 100, "emptyTerm", "term");

        verify(summaryDAO).find(any(), depthCaptor.capture(), eq(Race.TERRAN));
        //verify correct depth, 10 seconds to run the test just in case
        assertTrue(OffsetDateTime.now().minusDays(100).toEpochSecond() - depthCaptor.getValue().toEpochSecond() < 10);

        verify(followup).withContent(contentCaptor.capture());
        String content = contentCaptor.getValue();

        //verify output
        StringBuilder sb = new StringBuilder()
            .append("**1v1 Summary**\n")
            .append("*term, 100 days, Top 5, EU, Terran*\nGames | last/avg/max MMR\n\n");
        for(int i = 3; i > -1; i--)
        {
            sb.append(String.format(
                "[**[proTeam%1$s]proName%1$s** | [clan%1$s]name | tag#%1$s | " + DiscordBootstrap.SC2_REVEALED_TAG + "]" //unmasked name
                    + "(<https://www.nephest.com/sc2/?type=character&id=%1$s&m=1#player-stats-mmr>)\n" //web link
                    + DiscordBootstrap.REGION_EMOJIS.get(Region.EU) + " diamond terran" //region, league, race
                    + " | **%1$s** | " //games
                    + "**%1$s**/%2$s/%3$s\n\n", //last, avg, max mmr
                i, i * 2, i * 3)); //stubbed values

        }
        assertEquals(sb.toString(), content);
    }

    private void stub()
    {
        List<LadderDistinctCharacter> characters = new ArrayList<>();
        for(int i = 0; i < 5; i++) characters.add(DiscordTestUtil.createSimpleCharacter(
            "tag#" + i, "name#" + i, "proName" + i, "clan" + i, "proTeam" + i, Region.EU,
            i, i
        ));
        //excluded by region filter
        characters.add(DiscordTestUtil.createSimpleCharacter(
            "tag#6", "name#6", "6", "clan6", "proTeam6", Region.US, 6, 6
        ));
        when(ladderCharacterDAO.findDistinctCharacters("emptyTerm")).thenReturn(List.of());
        when(ladderCharacterDAO.findDistinctCharacters("term")).thenReturn(characters);

        List<PlayerCharacterSummary> summaries = new ArrayList<>();
        for(int i = 0; i < 4; i++) summaries.add(new PlayerCharacterSummary(
            (long) i, Race.TERRAN, i, i * 2, i * 3, i, BaseLeague.LeagueType.DIAMOND, i
        ));
        when(summaryDAO.find(eq(new Long[]{0L, 1L, 2L, 3L, 4L}), any(), eq(Race.TERRAN))).thenReturn(summaries);
        when(discordBootstrap.getLeagueEmojiOrName(BaseLeague.LeagueType.DIAMOND)).thenReturn("diamond");
        when(discordBootstrap.getRaceEmojiOrName(Race.TERRAN)).thenReturn("terran");
        when(evt.createFollowup()).thenReturn(followup);
    }

}
