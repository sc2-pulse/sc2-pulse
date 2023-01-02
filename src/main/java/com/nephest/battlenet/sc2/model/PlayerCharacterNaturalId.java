// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

public interface PlayerCharacterNaturalId
{

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
