// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

public interface PlayerCharacterNaturalId
{

    String TOON_HANDLE_REGEXP = "^([1235])-S2-(\\d+)-(\\d+)$";
    String TOON_HANDLE_DESCRIPTION = "regionId-S2-characterRealm-characterBattlenetId";

    static PlayerCharacterNaturalId of(Region region, Integer realm, Long battleNetId)
    {
        return new PlayerCharacterNaturalIdImpl(region, realm, battleNetId);
    }

    static PlayerCharacterNaturalId ofToonHandle(String toonHandle)
    {
        String[] split = toonHandle.split("-");
        if(split.length != 4)
            throw new IllegalArgumentException("4 section expected: " + toonHandle);
        if(!split[1].equals("S2"))
            throw new IllegalArgumentException("The second section must be S2: " + toonHandle);

        return new PlayerCharacterNaturalIdImpl
        (
            Region.from(Integer.parseInt(split[0])),
            Integer.parseInt(split[2]),
            Long.parseLong(split[3])
        );
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

    default String toToonHandle()
    {
        return getRegion().getId() + "-S2-" + getRealm() + "-" + getBattlenetId();
    }

}
