// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import java.io.Serializable;
import java.util.Objects;
import javax.validation.constraints.NotNull;

public class ClanMember
implements Serializable
{

    private static final long serialVersionUID = 3L;

    @NotNull
    private Long playerCharacterId;

    @NotNull
    private Integer clanId;

    public ClanMember()
    {
    }

    public ClanMember(Long playerCharacterId, Integer clanId)
    {
        this.playerCharacterId = playerCharacterId;
        this.clanId = clanId;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof ClanMember)) {return false;}
        ClanMember that = (ClanMember) o;
        return getPlayerCharacterId().equals(that.getPlayerCharacterId());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getPlayerCharacterId());
    }

    @Override
    public String toString()
    {
        return "ClanMember{" + "playerCharacterId=" + playerCharacterId + '}';
    }

    public Long getPlayerCharacterId()
    {
        return playerCharacterId;
    }

    public void setPlayerCharacterId(Long playerCharacterId)
    {
        this.playerCharacterId = playerCharacterId;
    }

    public Integer getClanId()
    {
        return clanId;
    }

    public void setClanId(Integer clanId)
    {
        this.clanId = clanId;
    }

}
