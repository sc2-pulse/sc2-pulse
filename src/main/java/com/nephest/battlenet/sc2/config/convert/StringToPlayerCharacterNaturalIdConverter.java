// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class StringToPlayerCharacterNaturalIdConverter
implements Converter<String, PlayerCharacterNaturalId>
{

    @Override
    public PlayerCharacterNaturalId convert(@NotNull String source)
    {
        return PlayerCharacterNaturalId.ofToonHandle(source);
    }

}
