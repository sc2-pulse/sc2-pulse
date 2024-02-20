// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.inner;

import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import java.util.Objects;

public class AccountCharacterData
{

    private final Account account;
    private final PlayerCharacter character;
    private final Boolean fresh;
    private final Integer season;

    public AccountCharacterData
    (
        Account account,
        PlayerCharacter character,
        Boolean fresh,
        Integer season
    )
    {
        this.account = account;
        this.character = character;
        this.fresh = fresh;
        this.season = season;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof AccountCharacterData)) {return false;}
        AccountCharacterData that = (AccountCharacterData) o;
        return Objects.equals(getAccount(), that.getAccount())
            && Objects.equals(getCharacter(), that.getCharacter())
            && Objects.equals(fresh, that.fresh)
            && Objects.equals(getSeason(), that.getSeason());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getAccount(), getCharacter(), fresh, getSeason());
    }

    @Override
    public String toString()
    {
        return "AccountCharacterData{" + "account=" + account + ", character=" + character
            + ", fresh=" + fresh + ", season=" + season + '}';
    }

    public Account getAccount()
    {
        return account;
    }

    public PlayerCharacter getCharacter()
    {
        return character;
    }

    public Boolean isFresh()
    {
        return fresh;
    }

    public Integer getSeason()
    {
        return season;
    }

}
