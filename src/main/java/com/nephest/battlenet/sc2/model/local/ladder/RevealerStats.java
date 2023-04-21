// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.local.Account;

public class RevealerStats
{

    private Account revealer;
    private Integer accountsRevealed;

    public RevealerStats()
    {
    }

    public RevealerStats(Account revealer, Integer accountsRevealed)
    {
        this.revealer = revealer;
        this.accountsRevealed = accountsRevealed;
    }

    public Account getRevealer()
    {
        return revealer;
    }

    public void setRevealer(Account revealer)
    {
        this.revealer = revealer;
    }

    public Integer getAccountsRevealed()
    {
        return accountsRevealed;
    }

    public void setAccountsRevealed(Integer accountsRevealed)
    {
        this.accountsRevealed = accountsRevealed;
    }

}
