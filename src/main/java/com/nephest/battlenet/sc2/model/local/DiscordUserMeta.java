// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonDeserialize(as = AccountDiscordUser.class)
@JsonSerialize(as = DiscordUserMeta.class)
public interface DiscordUserMeta
{

    Boolean isPublic();

}
