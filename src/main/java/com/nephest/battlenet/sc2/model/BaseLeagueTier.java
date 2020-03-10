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
package com.nephest.battlenet.sc2.model;

import javax.validation.constraints.*;

import com.fasterxml.jackson.annotation.*;

public class BaseLeagueTier
{

    public static enum LeagueTierType
    implements Identifiable
    {

        FIRST(0), SECOND(1), THIRD(2);

        private final int id;

        private LeagueTierType(int id)
        {
            this.id = id;
        }

        @JsonCreator
        public static final LeagueTierType from(int id)
        {
            for (LeagueTierType type : LeagueTierType.values())
            {
                if (type.getId() == id) return type;
            }

            throw new IllegalArgumentException("Invalid id");
        }

        @Override
        @JsonValue
        public int getId()
        {
            return id;
        }

    }

    @NotNull
    private LeagueTierType type;

    @NotNull
    private Integer minRating;

    @NotNull
    private Integer maxRating;

    public BaseLeagueTier(){}

    public BaseLeagueTier(LeagueTierType type, Integer minRating, Integer maxRating)
    {
        this.type = type;
        this.minRating = minRating;
        this.maxRating = maxRating;
    }

    public void setType(LeagueTierType type)
    {
    this.type = type;
    }

    public LeagueTierType getType()
    {
        return type;
    }

    public void setMinRating(Integer minRating)
    {
        this.minRating = minRating;
    }

    public Integer getMinRating()
    {
        return minRating;
    }

    public void setMaxRating(Integer maxRating)
    {
        this.maxRating = maxRating;
    }

    public Integer getMaxRating()
    {
        return maxRating;
    }

}


