// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Objects;

public class ProPlayerAccount
implements java.io.Serializable
{

    private static final long serialVersionUID = 1L;

    @NotNull
    private Long proPlayerId;

    @NotNull
    private Long accountId;

    @NotNull
    private OffsetDateTime updated = OffsetDateTime.now();

    public ProPlayerAccount(@NotNull Long proPlayerId, @NotNull Long accountId)
    {
        this.proPlayerId = proPlayerId;
        this.accountId = accountId;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProPlayerAccount that = (ProPlayerAccount) o;
        return proPlayerId.equals(that.proPlayerId) && accountId.equals(that.accountId);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(proPlayerId, accountId);
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s]",
            getClass().getSimpleName(),
            proPlayerId, accountId
        );
    }

    public Long getProPlayerId()
    {
        return proPlayerId;
    }

    public void setProPlayerId(Long proPlayerId)
    {
        this.proPlayerId = proPlayerId;
    }

    public Long getAccountId()
    {
        return accountId;
    }

    public void setAccountId(Long accountId)
    {
        this.accountId = accountId;
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
