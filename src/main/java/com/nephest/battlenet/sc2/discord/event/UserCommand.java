// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.event;

import discord4j.core.event.domain.interaction.UserInteractionEvent;

public interface UserCommand
extends DiscordApplicationCommand<UserInteractionEvent>
{

    static boolean isInvokedOnSelf(UserInteractionEvent evt)
    {
        return evt.getResolvedUser().getId().asLong()
            == evt.getInteraction().getUser().getId().asLong();
    }

    @Override
    default boolean isEphemeral()
    {
        return true;
    }

    @Override
    default boolean supportsMetaOptions()
    {
        return false;
    }

}
