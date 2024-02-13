// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import java.time.Instant;

public class ClanMemberEventData
{

    private final PlayerCharacter character;
    private final Clan clan;
    private final Instant createdAt;

    public ClanMemberEventData(PlayerCharacter character, Clan clan, Instant createdAt)
    {
        this.character = character;
        this.clan = clan;
        this.createdAt = createdAt;
    }

    public PlayerCharacter getCharacter()
    {
        return character;
    }

    public Clan getClan()
    {
        return clan;
    }

    public Instant getCreatedAt()
    {
        return createdAt;
    }

}
