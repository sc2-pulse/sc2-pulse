// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Objects;

public class ProTeamMember
implements java.io.Serializable
{

    private static final long serialVersionUID = 1L;

    @NotNull
    private Long proTeamId;

    @NotNull
    private Long proPlayerId;

    @NotNull
    private OffsetDateTime updated = OffsetDateTime.now();

    public ProTeamMember(){}

    public ProTeamMember(@NotNull Long proTeamId, @NotNull Long proPlayerId)
    {
        this.proTeamId = proTeamId;
        this.proPlayerId = proPlayerId;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProTeamMember that = (ProTeamMember) o;
        return getProPlayerId().equals(that.getProPlayerId());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getProPlayerId());
    }

    @Override
    public String toString()
    {
        return String.format("%s[%s]", ProTeamMember.class.getSimpleName(), getProPlayerId());
    }

    public Long getProTeamId()
    {
        return proTeamId;
    }

    public void setProTeamId(Long proTeamId)
    {
        this.proTeamId = proTeamId;
    }

    public Long getProPlayerId()
    {
        return proPlayerId;
    }

    public void setProPlayerId(Long proPlayerId)
    {
        this.proPlayerId = proPlayerId;
    }

    public OffsetDateTime getUpdated()
    {
        return updated;
    }

    public void setUpdated(OffsetDateTime updated)
    {
        this.updated = updated;
    }

}
