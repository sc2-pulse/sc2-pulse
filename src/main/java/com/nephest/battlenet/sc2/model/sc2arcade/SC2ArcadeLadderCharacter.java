// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.sc2arcade;

import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import com.nephest.battlenet.sc2.model.Region;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.lang.Nullable;

public record SC2ArcadeLadderCharacter
(
    @Nullable String region,
    @Nullable Integer realm,
    @Nullable String toonId,
    @Nullable String name,
    @Nullable String nameResolutionStatus
)
{

    public static final String RESOLVED_NAME_STATUS = "resolved";

    public Optional<PlayerCharacterNaturalId> naturalId(Region expectedRegion)
    {
        if
        (
            realm == null
            || !expectedRegion.name().equalsIgnoreCase(region)
            || !NumberUtils.isDigits(toonId)
        ) return Optional.empty();

        return Optional.of(PlayerCharacterNaturalId
            .of(expectedRegion, realm, NumberUtils.toLong(toonId)));
    }

    public Optional<String> resolvedName()
    {
        return RESOLVED_NAME_STATUS.equalsIgnoreCase(nameResolutionStatus)
            && StringUtils.isNotBlank(name)
                ? Optional.of(name)
                : Optional.empty();
    }

}
