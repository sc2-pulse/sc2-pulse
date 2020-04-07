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

import com.nephest.battlenet.sc2.model.BaseSeason;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardSeason;

import javax.validation.constraints.NotNull;
import java.util.Objects;

public class Season
extends BaseSeason
implements java.io.Serializable
{

    private static final long serialVersionUID = 1l;

    private Long id;

    @NotNull
    private Long battlenetId;

    @NotNull
    private Region region;

    public Season(){}

    public Season
    (
        Long id,
        Long battlenetId,
        Region region,
        Integer year,
        Integer number
    )
    {
        super(year, number);
        this.id = id;
        this.battlenetId = battlenetId;
        this.region = region;
    }

    public static final Season of(BlizzardSeason season, Region region)
    {
        return new Season
        (
            null,
            season.getId(),
            region,
            season.getYear(),
            season.getNumber()
        );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getBattlenetId(), getRegion());
    }

    @Override
    public boolean equals(Object other)
    {
        if (other == null) return false;
        if (this == other) return true;
        if (!(other instanceof Season)) return false;

        Season otherSeason = (Season) other;
        return getBattlenetId() == otherSeason.getBattlenetId()
            && getRegion() == otherSeason.getRegion();
    }

    @Override
    public String toString()
    {
        return String.format
        (
            "%s[%s %s]",
            getClass().getSimpleName(),
            String.valueOf(getBattlenetId()), getRegion().toString()
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

    public void setBattlenetId(Long battlenetId)
    {
        this.battlenetId = battlenetId;
    }

    public Long getBattlenetId()
    {
        return battlenetId;
    }

    public void setRegion(Region region)
    {
        this.region = region;
    }

    public Region getRegion()
    {
        return region;
    }

}
