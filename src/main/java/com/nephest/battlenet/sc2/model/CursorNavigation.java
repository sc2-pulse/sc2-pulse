// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.nephest.battlenet.sc2.config.convert.jackson.CursorNavigationDeserializer;
import com.nephest.battlenet.sc2.config.convert.jackson.KeysetScrollPositionKeysToStringSerializer;
import jakarta.annotation.Nullable;
import org.springframework.data.domain.KeysetScrollPosition;

@JsonDeserialize(using = CursorNavigationDeserializer.class)
public record CursorNavigation
(
    @Nullable @JsonSerialize(using = KeysetScrollPositionKeysToStringSerializer.class)
    KeysetScrollPosition before,
    @Nullable @JsonSerialize(using = KeysetScrollPositionKeysToStringSerializer.class)
    KeysetScrollPosition after
)
{

    public static final CursorNavigation EMPTY = new CursorNavigation(null, null);

}
