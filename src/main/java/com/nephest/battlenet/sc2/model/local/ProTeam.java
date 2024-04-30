// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.BaseProTeam;
import com.nephest.battlenet.sc2.model.aligulac.AligulacProPlayer;
import com.nephest.battlenet.sc2.model.aligulac.AligulacProTeam;
import com.nephest.battlenet.sc2.model.revealed.RevealedProPlayer;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import java.time.OffsetDateTime;
import java.util.Objects;
import javax.validation.constraints.NotNull;

public class ProTeam
extends BaseProTeam
implements java.io.Serializable
{

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long aligulacId;

    @NotNull
    private OffsetDateTime updated = SC2Pulse.offsetDateTime();

    public ProTeam()
    {
        super();
    }

    public ProTeam(Long id, Long aligulacId, @NotNull String name, String shortName)
    {
        super(name, shortName);
        this.id = id;
        this.aligulacId = aligulacId;
    }

    public static ProTeam of(RevealedProPlayer revealedProPlayer)
    {
        if(revealedProPlayer.getCurrentTeam() == null) return null;
        return new ProTeam(null, null, revealedProPlayer.getCurrentTeam(), null);
    }

    public static ProTeam of(AligulacProPlayer aligulacProPlayer)
    {
        if(aligulacProPlayer.getCurrentTeams().length == 0) return null;
        AligulacProTeam aligulacProTeam = aligulacProPlayer.getCurrentTeams()[0].getTeam();
        return new ProTeam(null, aligulacProTeam.getId(), aligulacProTeam.getName(), aligulacProTeam.getShortName());
    }

    /*
        Name is the only common field between sc2revealed and aligulac teams. Even though it is ugly and inefficient,
        it is the only way to do it right now.
     */
    @Override
    public boolean equals(Object o)
    {
        if(o == null) return false;
        if(o == this) return true;
        if ( !(o instanceof ProTeam) ) return false;
        ProTeam otherTeam = (ProTeam) o;
        return getUniqueName().equals(otherTeam.getUniqueName());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getUniqueName());
    }

    @Override
    public String toString()
    {
        return String.format("%s[%s]", ProTeam.class.getSimpleName(), getUniqueName());
    }

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getAligulacId()
    {
        return aligulacId;
    }

    public void setAligulacId(Long aligulacId)
    {
        this.aligulacId = aligulacId;
    }

    public OffsetDateTime getUpdated()
    {
        return updated;
    }

    public void setUpdated(OffsetDateTime updated)
    {
        this.updated = updated;
    }

    public String getUniqueName()
    {
        return getName().replace(" ", "").toLowerCase();
    }

}
