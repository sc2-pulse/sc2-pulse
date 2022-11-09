// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import java.time.OffsetDateTime;
import java.util.Objects;
import javax.validation.constraints.NotNull;

public class AuthenticationRequest
implements java.io.Serializable
{

    private static final long serialVersionUID = 1L;

    @NotNull
    private String name;

    @NotNull
    private OffsetDateTime created;

    public AuthenticationRequest()
    {
    }

    public AuthenticationRequest(String name, OffsetDateTime created)
    {
        this.name = name;
        this.created = created;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof AuthenticationRequest)) {return false;}
        AuthenticationRequest that = (AuthenticationRequest) o;
        return getName().equals(that.getName());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getName());
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public OffsetDateTime getCreated()
    {
        return created;
    }

    public void setCreated(OffsetDateTime created)
    {
        this.created = created;
    }

}
