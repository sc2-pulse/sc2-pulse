/*-
 * =========================LICENSE_START=========================
 * SC2 Ladder Generator
 * %%
 * Copyright (C) 2020 Oleksandr Masniuk
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END=========================
 */
package com.nephest.battlenet.sc2.model.local;

import java.util.Objects;

import javax.validation.constraints.NotNull;

import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLeagueTier;

public class LeagueTier
extends BaseLeagueTier
implements java.io.Serializable
{

    private static final long serialVersionUID = 1l;

    private Long id;

    @NotNull
    private Long leagueId;

    public LeagueTier(){}

    public LeagueTier(Long id, Long leagueId, LeagueTierType type, Integer minRating, Integer maxRating)
    {
        super(type, minRating, maxRating);
        this.id = id;
        this.leagueId = leagueId;
    }

    public static final LeagueTier of(League league, BlizzardLeagueTier bTier)
    {
        return new LeagueTier
        (
            null,
            league.getId(),
            bTier.getType(),
            bTier.getMinRating(),
            bTier.getMaxRating()
        );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getLeagueId(), getType());
    }

    @Override
    public boolean equals(Object other)
    {
        if (other == null) return false;
        if (other == this) return true;
        if ( !(other instanceof LeagueTier) ) return false;

        LeagueTier otherTier = (LeagueTier) other;
        return getLeagueId() == otherTier.getLeagueId()
            && getType() == otherTier.getType();
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s]",
            getClass().getSimpleName(),
            String.valueOf(getLeagueId()), getType().toString()
        );
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }

    public void setLeagueId(Long leagueId)
    {
        this.leagueId = leagueId;
    }

    public Long getLeagueId()
    {
        return leagueId;
    }

}
