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

public class BaseLocalTeamMember
implements java.io.Serializable
{

    private static final long serialVersionUID = 1l;

    private Integer terranGamesPlayed;
    private Integer protossGamesPlayed;
    private Integer zergGamesPlayed;
    private Integer randomGamesPlayed;

    public BaseLocalTeamMember(){}

    public BaseLocalTeamMember
    (
        Integer terranGamesPlayed,
        Integer protossGamesPlayed,
        Integer zergGamesPlayed,
        Integer randomGamesPlayed
    )
    {
        this.terranGamesPlayed = terranGamesPlayed;
        this.protossGamesPlayed = protossGamesPlayed;
        this.zergGamesPlayed = zergGamesPlayed;
        this.randomGamesPlayed = randomGamesPlayed;
    }

    public void setTerranGamesPlayed(Integer terranGamesPlayed)
    {
        this.terranGamesPlayed = terranGamesPlayed;
    }

    public Integer getTerranGamesPlayed()
    {
        return terranGamesPlayed;
    }

    public void setProtossGamesPlayed(Integer protossGamesPlayed)
    {
        this.protossGamesPlayed = protossGamesPlayed;
    }

    public Integer getProtossGamesPlayed()
    {
        return protossGamesPlayed;
    }

    public void setZergGamesPlayed(Integer zergGamesPlayed)
    {
        this.zergGamesPlayed = zergGamesPlayed;
    }

    public Integer getZergGamesPlayed()
    {
        return zergGamesPlayed;
    }

    public void setRandomGamesPlayed(Integer randomGamesPlayed)
    {
        this.randomGamesPlayed = randomGamesPlayed;
    }

    public Integer getRandomGamesPlayed()
    {
        return randomGamesPlayed;
    }

}
