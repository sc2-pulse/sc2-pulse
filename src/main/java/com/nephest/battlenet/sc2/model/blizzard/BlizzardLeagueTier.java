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
package com.nephest.battlenet.sc2.model.blizzard;

import java.util.Objects;

import javax.validation.*;
import javax.validation.constraints.*;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import static com.fasterxml.jackson.databind.PropertyNamingStrategy.*;

import com.nephest.battlenet.sc2.model.*;

@JsonNaming(SnakeCaseStrategy.class)
public class BlizzardLeagueTier
extends BaseLeagueTier
{

    private static final BlizzardTierDivision[] EMPTY_DIVISION_ARRAY
        = new BlizzardTierDivision[0];

    //divisions can be empty
    @Valid
    @JsonProperty("division")
    private BlizzardTierDivision[] divisions = EMPTY_DIVISION_ARRAY;

    public BlizzardLeagueTier(){}

    public BlizzardLeagueTier(LeagueTierType type, int minRating, int maxRating)
    {
        super(type, minRating, maxRating);
    }

    @JsonProperty("id")
    @Override
    public void setType(LeagueTierType type)
    {
        super.setType(type);
    }

    @JsonProperty("id")
    @Override
    public LeagueTierType getType()
    {
        return super.getType();
    }

    public void setDivisions(BlizzardTierDivision[] divisions)
    {
        this.divisions = divisions;
    }

    public BlizzardTierDivision[] getDivisions()
    {
        return divisions;
    }

}
