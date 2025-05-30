// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.discord;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.discord.InstallationData;
import discord4j.discordjson.json.ApplicationInfoData;
import discord4j.discordjson.possible.Possible;
import org.junit.jupiter.api.Test;

public class InstallationDataTest
{

    @Test
    @SuppressWarnings("deprecation")
    public void testApplicationInfoDataConstructor()
    {
        ApplicationInfoData infoData = ApplicationInfoData.builder()
            .name("name")
            .description("desc")
            .id("123")
            .summary("ignored")
            .botPublic(false)
            .botRequireCodeGrant(false)
            .verifyKey("key")
            .approximateGuildCount(Possible.absent())
            .approximateUserInstallCount(Possible.of(3))
            .build();
        InstallationData data = new InstallationData(infoData);
        assertEquals(0, data.guildCount());
        assertEquals(3, data.userCount());
    }

}
