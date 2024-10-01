// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.linked;

import com.nephest.battlenet.sc2.discord.Discord;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.discord.dao.DiscordUserDAO;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component @Discord
public class LinkedDiscordAccountSupplier
implements LinkedAccountSupplier
{

    private final DiscordUserDAO discordUserDAO;

    @Autowired
    public LinkedDiscordAccountSupplier(DiscordUserDAO discordUserDAO)
    {
        this.discordUserDAO = discordUserDAO;
    }

    @Override
    public SocialMedia getSocialMedia()
    {
        return SocialMedia.DISCORD;
    }

    @Override
    public Optional<?> getAccountByPulseAccountId(long pulseAccountId)
    {
        return discordUserDAO.findByAccountId(pulseAccountId, true);
    }

}
