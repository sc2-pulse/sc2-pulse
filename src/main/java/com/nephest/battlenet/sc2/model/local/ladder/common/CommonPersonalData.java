// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.common;

import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.AccountFollowing;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDiscordUser;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;

public class CommonPersonalData
{

    @NotNull
    private final Account account;

    @NotNull
    private final Collection<? extends SC2PulseAuthority> roles;

    @NotNull
    private final List<LadderDistinctCharacter> characters;

    @NotNull
    private final List<AccountFollowing> accountFollowings;

    @NotNull
    private final List<LadderDistinctCharacter> followingCharacters;

    private final LadderDiscordUser discordUser;

    public CommonPersonalData
    (
        Account account,
        Collection<? extends SC2PulseAuthority> roles,
        List<LadderDistinctCharacter> characters,
        List<AccountFollowing> accountFollowings,
        List<LadderDistinctCharacter> followingCharacters,
        LadderDiscordUser discordUser
    )
    {
        this.account = account;
        this.roles = roles;
        this.characters = characters;
        this.accountFollowings = accountFollowings;
        this.followingCharacters = followingCharacters;
        this.discordUser = discordUser;
    }

    public Account getAccount()
    {
        return account;
    }

    public Collection<? extends SC2PulseAuthority> getRoles()
    {
        return roles;
    }

    public List<LadderDistinctCharacter> getCharacters()
    {
        return characters;
    }

    public List<AccountFollowing> getAccountFollowings()
    {
        return accountFollowings;
    }

    public List<LadderDistinctCharacter> getFollowingCharacters()
    {
        return followingCharacters;
    }

    public LadderDiscordUser getDiscordUser()
    {
        return discordUser;
    }

}
