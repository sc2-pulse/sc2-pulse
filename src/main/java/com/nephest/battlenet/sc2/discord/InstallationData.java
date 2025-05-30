// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord;

import discord4j.discordjson.json.ApplicationInfoData;

public record InstallationData(int guildCount, int userCount)
{

    public InstallationData(ApplicationInfoData info)
    {
        this
        (
            info.approximateGuildCount().toOptional().orElse(0),
            info.approximateUserInstallCount().toOptional().orElse(0)
        );
    }

}
