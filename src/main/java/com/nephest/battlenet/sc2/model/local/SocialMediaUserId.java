// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.SocialMedia;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class SocialMediaUserId
{

    @NotNull
    private final SocialMedia type;

    @NotNull
    private final String serviceUserid;

    public SocialMediaUserId(SocialMedia type, String serviceUserid)
    {
        this.type = type;
        this.serviceUserid = serviceUserid;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof SocialMediaUserId that)) {return false;}
        return getType() == that.getType()
            && Objects.equals(getServiceUserid(), that.getServiceUserid());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getType(), getServiceUserid());
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s]",
            SocialMediaUserId.class.getSimpleName(),
            getType(),
            getServiceUserid()
        );
    }

    public SocialMedia getType()
    {
        return type;
    }

    public String getServiceUserid()
    {
        return serviceUserid;
    }

}
