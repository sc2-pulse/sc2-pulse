// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class PlayerCharacterNaturalIdImpl
implements PlayerCharacterNaturalId
{

    @NotNull
    private final Region region;

    @NotNull
    private final Integer realm;

    @NotNull
    private final Long battlenetId;

    public PlayerCharacterNaturalIdImpl
    (
        @NotNull Region region,
        @NotNull Integer realm,
        @NotNull Long battlenetId
    )
    {
        this.region = region;
        this.realm = realm;
        this.battlenetId = battlenetId;
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof PlayerCharacterNaturalIdImpl that)) {return false;}
        return getRegion() == that.getRegion()
            && Objects.equals(getRealm(), that.getRealm())
            && Objects.equals(getBattlenetId(), that.getBattlenetId());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getRegion(), getRealm(), getBattlenetId());
    }

    @Override
    public String toString()
    {
        return "PlayerCharacterNaturalIdImpl{"
            + "region=" + region
            + ", realm=" + realm
            + ", battlenetId=" + battlenetId
        + '}';
    }

    @Override
    public Region getRegion()
    {
        return region;
    }

    @Override
    public Integer getRealm()
    {
        return realm;
    }

    @Override
    public Long getBattlenetId()
    {
        return battlenetId;
    }

}
