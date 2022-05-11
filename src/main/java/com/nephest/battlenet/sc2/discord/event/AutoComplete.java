// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.event;

import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;

public interface AutoComplete
extends NamedCommand
{

    int DEFAULT_SUGGESTIONS_SIZE = 10;

    Iterable<ApplicationCommandOptionChoiceData> autoComplete(ChatInputAutoCompleteEvent evt);

}
