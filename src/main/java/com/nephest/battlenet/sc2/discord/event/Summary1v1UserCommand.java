// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.discord.event;

import com.nephest.battlenet.sc2.discord.Discord;
import com.nephest.battlenet.sc2.discord.DiscordBootstrap;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import discord4j.core.event.domain.interaction.UserInteractionEvent;
import discord4j.core.object.command.ApplicationCommand;
import discord4j.core.object.entity.Message;
import discord4j.discordjson.json.ImmutableApplicationCommandRequest;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Discord
public class Summary1v1UserCommand
implements UserCommand
{

    public static final Pattern USER_NAME_SANITIZER = Pattern.compile("[^\\p{L}\\p{N}]");
    public static final String VERIFIED_ACCOUNT_DESCRIPTION = ":white_check_mark: Verified";
    public static final String UNVERIFIED_ACCOUNT_DESCRIPTION =
        ":grey_question: Unverified, searching by discord username";
    private final String unverifiedAccountVerifyDescription;

    private final AccountDAO accountDAO;
    private final Summary1v1Command summary1v1Command;
    private final DiscordBootstrap discordBootstrap;

    @Autowired
    public Summary1v1UserCommand
    (
        AccountDAO accountDAO,
        Summary1v1Command summary1v1Command,
        DiscordBootstrap discordBootstrap
    )
    {
        this.accountDAO = accountDAO;
        this.summary1v1Command = summary1v1Command;
        this.discordBootstrap = discordBootstrap;
        this.unverifiedAccountVerifyDescription = UNVERIFIED_ACCOUNT_DESCRIPTION
            + "(" + discordBootstrap.getAccountVerificationLink() + ")";
    }

    @Override
    public ImmutableApplicationCommandRequest.Builder generateCommandRequest()
    {
        return ImmutableApplicationCommandRequest.builder()
            .name(Summary1v1Command.CMD_NAME)
            .type(ApplicationCommand.Type.USER.getValue())
            .integrationTypes(DiscordApplicationCommand.SUPPORTED_INTEGRATION_TYPES);
    }

    @Override
    public String getCommandName()
    {
        return Summary1v1Command.CMD_NAME;
    }

    @Override
    public Mono<Message> handle(UserInteractionEvent evt)
    {
        Optional<Account> acc =
            accountDAO.findByDiscordUserId(evt.getResolvedUser().getId().asLong());
        if(acc.isPresent())
        {
            return summary1v1Command.handle
            (
                evt,
                VERIFIED_ACCOUNT_DESCRIPTION,
                null,
                null,
                Summary1v1Command.DEFAULT_DEPTH,
                acc.get().getBattleTag()
            );
        }
        else
        {
            String displayName = sanitizeName(discordBootstrap.getTargetDisplayNameOrName(evt).block());
            String name = sanitizeName(evt.getResolvedUser().getUsername());
            String[] names = displayName.equals(name)
                ? new String[]{displayName}
                : new String[]{displayName, name};
            boolean selfCommand = evt.getResolvedUser().getId().asLong()
                == evt.getInteraction().getUser().getId().asLong();
            return summary1v1Command.handle
            (
                evt,
                selfCommand ? unverifiedAccountVerifyDescription : UNVERIFIED_ACCOUNT_DESCRIPTION,
                null,
                null,
                Summary1v1Command.DEFAULT_DEPTH,
                names
            );
        }
    }

    public static String sanitizeName(String name)
    {
        String[] split = USER_NAME_SANITIZER.split(name.trim());
        return split[split.length - 1];
    }

}
