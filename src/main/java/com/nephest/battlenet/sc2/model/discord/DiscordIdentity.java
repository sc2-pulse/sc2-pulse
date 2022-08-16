// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.discord;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Provides information required by real user(human) to find the target user.
 */
@JsonDeserialize(as = DiscordUser.class)
@JsonSerialize(as = DiscordIdentity.class)
public interface DiscordIdentity
{

    String getName();

    Integer getDiscriminator();

}
