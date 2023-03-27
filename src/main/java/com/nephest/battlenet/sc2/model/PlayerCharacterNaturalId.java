// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import com.nephest.battlenet.sc2.model.local.PlayerCharacter;

public interface PlayerCharacterNaturalId
{

    static PlayerCharacterNaturalId of(Region region, Integer realm, Long battleNetId)
    {
        return new PlayerCharacter(null, null, region, battleNetId, realm, null);
    }

    Region getRegion();

    Integer getRealm();

    Long getBattlenetId();

    default String generateProfileSuffix()
    {
        return "/" + getRegion().getId()
            + "/" + getRealm()
            + "/" + getBattlenetId();
    }

}
