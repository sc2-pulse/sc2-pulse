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

public class BasePlayerCharacter
{

    @NotNull
    private Integer realm;

    @NotNull
    private String name;

    public BasePlayerCharacter(){}

    public BasePlayerCharacter(Integer realm, String name)
    {
        this.realm = realm;
        this.name = name;
    }

    public void setRealm(Integer realm)
    {
        this.realm = realm;
    }

    public Integer getRealm()
    {
        return realm;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return name;
    }

}
