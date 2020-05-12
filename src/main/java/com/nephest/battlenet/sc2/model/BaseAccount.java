// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import javax.validation.constraints.NotNull;

public class BaseAccount
{

    @NotNull
    private String battleTag;

    public BaseAccount(){}

    public BaseAccount(String battleTag)
    {
        this.battleTag = battleTag;
    }

    public void setBattleTag(String battleTag)
    {
        this.battleTag = battleTag;
    }

    public String getBattleTag()
    {
        return battleTag;
    }

}
