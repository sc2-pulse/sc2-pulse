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

import javax.validation.constraints.NotNull;

public class BaseTeam
{

    @NotNull
    private Long rating;

    @NotNull
    private Integer wins;

    @NotNull
    private Integer losses;

    @NotNull
    private Integer ties;

    @NotNull
    private Integer points;

    public BaseTeam(){}

    public BaseTeam(Long rating, Integer wins, Integer losses, Integer ties, Integer points)
    {
        setRating(rating);
        this.wins = wins;
        this.losses = losses;
        this.ties = ties;
        this.points = points;
    }

    public void setRating(Long rating)
    {
        this.rating = rating;
    }

    public Long getRating()
    {
        return rating;
    }

    public void setWins(Integer wins)
    {
        this.wins = wins;
    }

    public Integer getWins()
    {
        return wins;
    }

    public void setLosses(Integer losses)
    {
        this.losses = losses;
    }

    public Integer getLosses()
    {
        return losses;
    }

    public void setTies(Integer ties)
    {
        this.ties = ties;
    }

    public Integer getTies()
    {
        return ties;
    }

    public void setPoints(Integer points)
    {
        this.points = points;
    }

    public Integer getPoints()
    {
        return points;
    }

}
