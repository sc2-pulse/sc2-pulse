// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import java.time.OffsetDateTime;
import java.util.Objects;
import javax.validation.constraints.NotNull;

public class ProPlayerAccount
implements java.io.Serializable
{

    private static final long serialVersionUID = 3L;

    @NotNull
    private Long proPlayerId;

    @NotNull
    private Long accountId;

    private Long revealerAccountId;

    @NotNull
    private OffsetDateTime updated = OffsetDateTime.now();

    @NotNull
    private Boolean isProtected;

    public ProPlayerAccount(@NotNull Long proPlayerId, @NotNull Long accountId)
    {
        this.proPlayerId = proPlayerId;
        this.accountId = accountId;
        this.isProtected = false;
    }

    public ProPlayerAccount(Long proPlayerId, Long accountId, OffsetDateTime updated, Boolean isProtected)
    {
        this.proPlayerId = proPlayerId;
        this.accountId = accountId;
        this.updated = updated;
        this.isProtected = isProtected;
    }

    public ProPlayerAccount
    (
        Long proPlayerId,
        Long accountId,
        Long revealerAccountId,
        OffsetDateTime updated,
        Boolean isProtected
    )
    {
        this.proPlayerId = proPlayerId;
        this.accountId = accountId;
        this.revealerAccountId = revealerAccountId;
        this.updated = updated;
        this.isProtected = isProtected;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProPlayerAccount that = (ProPlayerAccount) o;
        return accountId.equals(that.accountId);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(accountId);
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s]",
            ProPlayerAccount.class.getSimpleName(),
            accountId
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

    public Long getRevealerAccountId()
    {
        return revealerAccountId;
    }

    public void setRevealerAccountId(Long revealerAccountId)
    {
        this.revealerAccountId = revealerAccountId;
    }

    public OffsetDateTime getUpdated()
    {
        return updated;
    }

    public void setUpdated(OffsetDateTime updated)
    {
        this.updated = updated;
    }

    public Boolean isProtected()
    {
        return isProtected;
    }

    public void setProtected(Boolean aProtected)
    {
        isProtected = aProtected;
    }

}
