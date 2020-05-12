// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Objects;

public class AccountFollowing
implements Serializable
{

    private static final long serialVersionUID = 1L;

    @NotNull
    private Long accountId;

    @NotNull
    private Long followingAccountId;

    public AccountFollowing(@NotNull Long accountId, @NotNull Long followingAccountId)
    {
        this.accountId = accountId;
        this.followingAccountId = followingAccountId;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountFollowing that = (AccountFollowing) o;
        return getAccountId().equals(that.getAccountId()) && getFollowingAccountId().equals(that.getFollowingAccountId());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getAccountId(), getFollowingAccountId());
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s]",
            getClass().getSimpleName(), getAccountId(), getFollowingAccountId()
        );
    }

    public Long getAccountId()
    {
        return accountId;
    }

    public void setAccountId(Long accountId)
    {
        this.accountId = accountId;
    }

    public Long getFollowingAccountId()
    {
        return followingAccountId;
    }

    public void setFollowingAccountId(Long followingAccountId)
    {
        this.followingAccountId = followingAccountId;
    }
}
