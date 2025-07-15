// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

public interface PlayerCharacterNaturalId
{

    static PlayerCharacterNaturalId of(Region region, Integer realm, Long battleNetId)
    {
        return new PlayerCharacterNaturalIdImpl(region, realm, battleNetId);
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
