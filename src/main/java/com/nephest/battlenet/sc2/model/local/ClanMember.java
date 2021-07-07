// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Objects;

public class ClanMember
implements java.io.Serializable
{

    private static final long serialVersionUID = 1L;

    @NotNull
    private Integer clanId;

    @NotNull
    private Long characterId;

    @NotNull
    private OffsetDateTime updated;

    public ClanMember(){}

    public ClanMember(Integer clanId, Long characterId, OffsetDateTime updated)
    {
        this.clanId = clanId;
        this.characterId = characterId;
        this.updated = updated;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof ClanMember)) return false;
        ClanMember that = (ClanMember) o;
        return getCharacterId().equals(that.getCharacterId());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getCharacterId());
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s]",
            ClanMember.class.getSimpleName(),
            getCharacterId()
        );
    }

    public Integer getClanId()
    {
        return clanId;
    }

    public void setClanId(Integer clanId)
    {
        this.clanId = clanId;
    }

    public Long getCharacterId()
    {
        return characterId;
    }

    public void setCharacterId(Long characterId)
    {
        this.characterId = characterId;
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
