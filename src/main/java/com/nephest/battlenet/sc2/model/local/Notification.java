// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import java.time.OffsetDateTime;
import javax.validation.constraints.NotNull;

public class Notification
implements java.io.Serializable
{

    private static final long serialVersionUID = 1L;

    private Long id;

    @NotNull
    private Long accountId;

    @NotNull
    private String message;

    private OffsetDateTime created;

    public Notification(){}

    public Notification(Long accountId, String message)
    {
        this.accountId = accountId;
        this.message = message;
    }

    public Notification(Long id, Long accountId, String message, OffsetDateTime created)
    {
        this.id = id;
        this.accountId = accountId;
        this.message = message;
        this.created = created;
    }

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getAccountId()
    {
        return accountId;
    }

    public void setAccountId(Long accountId)
    {
        this.accountId = accountId;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
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
