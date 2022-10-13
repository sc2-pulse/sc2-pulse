// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.discord.DiscordBootstrap;
import com.nephest.battlenet.sc2.discord.GuildEmojiStore;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.web.service.UpdateService;
import com.nephest.battlenet.sc2.web.util.WebContextUtil;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DiscordBootstrapTest
{

    @Mock
    private GuildEmojiStore guildEmojiStore;

    @Mock
    private UpdateService updateService;

    @Mock
    private WebContextUtil webContextUtil;

    private DiscordBootstrap discordBootstrap;

    @BeforeEach
    public void beforeEach()
    {
        when(webContextUtil.getPublicUrl()).thenReturn("publicUrl");
        discordBootstrap = new DiscordBootstrap
        (
            Map.of(),
            Map.of(),
            guildEmojiStore,
            updateService,
            webContextUtil
        );
    }

    @Test
    public void testGenerateCharacterURL()
    {
        String expected = "[**[proTeam1]proName1** | [clan1]name | tag#1 | "
            + DiscordBootstrap.SC2_REVEALED_TAG + "]"
            + "(<publicUrl?type=character&id=1&m=1#player-stats-mmr>)";
        LadderTeamMember member = new LadderTeamMember
        (
            new Account(2L, Partition.GLOBAL, "tag#1"),
            new PlayerCharacter(1L, 2L, Region.EU, 2L, 2, "name#1"),
            new Clan(2, "clan1", Region.EU, "clanName"),
            "proName1", "proTeam1",
            null,
            1, 1, 1, 1
        );
        assertEquals(expected, discordBootstrap.generateCharacterURL(member));
    }

}
